package org.phonepe.service;

import org.phonepe.domain.AppVersion;
import org.phonepe.domain.Device;
import org.phonepe.domain.UpdatePlan;
import org.phonepe.enums.UpdateType;
import org.phonepe.rollout.RolloutStrategy;
import org.phonepe.store.AppStore;

import java.util.*;

import static org.phonepe.util.CompareVersion.compareVersions;

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
                                       int minAndroidVersion,
                                       String description,
                                       byte[] apkContent) {
        if (version == null || version.isEmpty() || apkContent == null || apkContent.length == 0) {
            System.out.println("[ERROR] Invalid upload request for version: " + version);
            return null;
        }
        if (minAndroidVersion <= 0) {
            System.out.println("[ERROR] Invalid minAndroidVersion for version: " + version);
            return null;
        }

        if (store.getVersion(version) != null) {
            System.out.println("[INFO] Version " + version + " already exists, skipping upload.");
            return store.getVersion(version);
        }
        String apkUrl = files.uploadFile(apkContent);
        AppVersion v = new AppVersion(version, minAndroidVersion, description, apkUrl);
        store.putVersion(v);
        System.out.println("[UPLOAD] Version " + version + " uploaded successfully.");
        return v;
    }

    // ------------ API: patch ------------
    public String createUpdatePatch(String fromVersion, String toVersion) {
        AppVersion from = getAppVersion(fromVersion);
        AppVersion to = getAppVersion(toVersion);

        if (from == null || to == null) {
            System.out.println("[ERROR] Cannot create patch: one or both versions are missing.");
            return null;
        }
        if (compareVersions(fromVersion, toVersion) >= 0) {
            System.out.println("[ERROR] Invalid patch order: fromVersion >= toVersion.");
            return null;
        }
        // Fast path: if someone already created it, reuse
        String existing = to.getDiffFrom(fromVersion);
        if (existing != null) {
            System.out.println("[INFO] Patch already exists: " + fromVersion + " -> " + toVersion);
            return existing;
        }

        byte[] fromApk = files.getFile(from.getApkUrl());
        byte[] toApk = files.getFile(to.getApkUrl());
        String diffUrl = files.uploadFile(diffs.createDiffPack(fromApk, toApk));
        to.addDiffPack(fromVersion, diffUrl);
        System.out.println("[PATCH] Diff created between " + fromVersion + " -> " + toVersion);
        return diffUrl;
    }

    // ------------ API: release ------------
    public void releaseVersion(String toVersion, RolloutStrategy strategy) {
        AppVersion v = store.getVersion(toVersion);
        if (v == null) {
            System.out.println("[ERROR] Cannot release: version " + toVersion + " not found.");
            return;
        }
        if (strategy == null) {
            System.out.println("[ERROR] Rollout strategy is null for version " + toVersion);
            return;
        }
        if (store.isReleased(toVersion)) {
            System.out.println("[INFO] Version " + toVersion + " already released. Skipping duplicate release.");
            return;
        }
        store.markReleased(toVersion, strategy);
    }

    // ------------ API: support check ------------
    public boolean isAppVersionSupported(String targetVersion, Device device) {
        AppVersion v = store.getVersion(targetVersion);
        if (v == null || !store.isReleased(targetVersion)) return false;
        if (device.getAndroidVersion() < v.getMinAndroidVersion()) return false;

        return store.getRollout(targetVersion)
                .map(strategy -> strategy.isEligible(device))
                .orElse(false);
    }

    // ------------ API: check updates ------------
    public Optional<UpdatePlan> checkForUpdates(Device device) {
        List<String> released = store.releasedVersionsSorted();
        if (released.isEmpty()) {
            System.out.println("[INFO] No released versions available.");
            return Optional.empty();
        }

        String current = device.getCurrentAppVersion();
        UpdatePlan plan = null;

        for (String release : released) {
            if (!isAppVersionSupported(release, device)) continue;

            if (current == null) {
                AppVersion to = store.getVersion(release);
                plan = new UpdatePlan(UpdateType.INSTALL, null, to, to.getApkUrl(), null);
                continue;
            }

            AppVersion to = store.getVersion(release);
            if(current.equals(to.getVersion())){
                return Optional.empty();
            }
            String diff = to.getDiffFrom(current);
            if (diff != null) {
                plan = new UpdatePlan(UpdateType.UPDATE, store.getVersion(current), to, null, diff);
            } else {
                plan = new UpdatePlan(UpdateType.INSTALL, store.getVersion(current), to, to.getApkUrl(), null);
            }
        }
        return Optional.ofNullable(plan);
    }

    public void executeTask(Device device, UpdatePlan plan) {
        if (plan == null) {
            System.out.println("[WARN] No update plan provided for device " + device.getDeviceId());
            return;
        }
        synchronized (device.lock()) {
            String curr   = device.getCurrentAppVersion();
            String target = plan.target().getVersion();

            // Idempotency: if another thread already set this target, skip
            if (curr != null && compareVersions(target, curr) <= 0) {
                System.out.println("[INFO] Device " + device.getDeviceId() + " already on " + curr + ", skipping.");
                return;
            }
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

    private AppVersion getAppVersion(String version) {
        AppVersion v = store.getVersion(version);
        if (v == null) {
            System.out.println("[ERROR] Unknown version: " + version);
            return null;
        }
        return v;
    }
}

