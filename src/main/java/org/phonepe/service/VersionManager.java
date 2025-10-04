package org.phonepe.service;

import org.phonepe.domain.AppVersion;
import org.phonepe.domain.Device;
import org.phonepe.domain.UpdatePlan;
import org.phonepe.enums.UpdateType;
import org.phonepe.rollout.RolloutStrategy;
import org.phonepe.store.AppStore;

import java.util.*;

/**
 * Orchestrates version upload, patches, releases, checks, and execution.
 */
public class VersionManager {
    private final AppStore store;
    private final FileService files;
    private final DiffService diffs;
    private final Installer installer;

    public VersionManager(AppStore store, FileService files, DiffService diffs, Installer installer) {
        this.store = store;
        this.files = files;
        this.diffs = diffs;
        this.installer = installer;
    }

    // ------------ API: upload ------------
    public AppVersion uploadNewVersion(String version,
                                       int minApi,
                                       String description,
                                       byte[] apkContent) {
        String apkUrl = files.uploadFile(apkContent);
        AppVersion v = new AppVersion(version, minApi, description, apkUrl);
        store.putVersion(v);
        return v;
    }

    // ------------ API: patch ------------
    public String createUpdatePatch(String fromVersion, String toVersion) {
        AppVersion from = mustGet(fromVersion);
        AppVersion to = mustGet(toVersion);

        // Fast path: if someone already created it, reuse
        String existing = to.getDiffFrom(fromVersion);
        if (existing != null) return existing;

        byte[] fromApk = files.getFile(from.getApkUrl());
        byte[] toApk = files.getFile(to.getApkUrl());
        byte[] pack = diffs.createDiffPack(fromApk, toApk);
        String diffUrl = files.uploadFile(pack);

        // Idempotent publish in case of a race
        return to.putDiffIfAbsent(fromVersion, diffUrl);
    }

    // ------------ API: release ------------
    public void releaseVersion(String toVersion, RolloutStrategy strategy) {
        mustGet(toVersion);
        store.markReleased(toVersion, strategy);
    }

    // ------------ API: support check ------------
    public boolean isAppVersionSupported(String targetVersion, Device device) {
        AppVersion v = store.getVersion(targetVersion);
        if (v == null || !store.isReleased(targetVersion)) return false;
        if (device.getAndroidApi() < v.getMinAndroidApi()) return false;

        return store.getRollout(targetVersion)
                .map(strategy -> strategy.isEligible(device))
                .orElse(false);
    }

    // ------------ API: check updates ------------
    public Optional<UpdatePlan> checkForUpdates(Device device) {
        List<String> released = store.releasedVersionsSorted();
        if (released.isEmpty()) return Optional.empty();

        String current = device.getCurrentAppVersion();
        UpdatePlan best = null;

        for (String candidate : released) {
            if (!isAppVersionSupported(candidate, device)) continue;

            if (current == null) {
                AppVersion to = store.getVersion(candidate);
                best = new UpdatePlan(UpdateType.INSTALL, null, to, to.getApkUrl(), null);
                continue;
            }

            AppVersion to = store.getVersion(candidate);
            String diff = to.getDiffFrom(current);
            if (diff != null) {
                best = new UpdatePlan(UpdateType.UPDATE, store.getVersion(current), to, null, diff);
            } else {
                best = new UpdatePlan(UpdateType.INSTALL, store.getVersion(current), to, to.getApkUrl(), null);
            }
        }
        return Optional.ofNullable(best);
    }

    public void executeTask(Device device, UpdatePlan plan) {
        if (plan == null) throw new IllegalArgumentException("plan is null");
        synchronized (device.lock()) {
            String curr   = device.getCurrentAppVersion();
            String target = plan.target().getVersion();

            // Idempotency: if another thread already set this target, skip
            if (curr != null && cmpVersion(target, curr) <= 0) return;

            switch (plan.type()) {
                case INSTALL -> {
                    installer.installApp(device, plan.apkUrl());
                    device.setCurrentAppVersion(plan.target().getVersion());
                }
                case UPDATE -> {
                    installer.updateApp(device, plan.diffUrl());
                    device.setCurrentAppVersion(plan.target().getVersion());
                }
            }
        }
    }

    private AppVersion mustGet(String version) {
        AppVersion v = store.getVersion(version);
        if (v == null) throw new IllegalArgumentException("Unknown version: " + version);
        return v;
    }

    private static int cmpVersion(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? safe(as[i]) : 0;
            int bi = i < bs.length ? safe(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
    private static int safe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

}

