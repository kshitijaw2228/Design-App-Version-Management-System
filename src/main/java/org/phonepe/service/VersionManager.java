package org.phonepe.service;

import org.phonepe.domain.AppVersion;
import org.phonepe.domain.Device;
import org.phonepe.domain.UpdatePlan;
import org.phonepe.enums.UpdateType;
import org.phonepe.rollout.RolloutStrategy;
import org.phonepe.store.AppStore;
import org.phonepe.util.CompareVersion;

import java.util.*;

import static org.phonepe.util.CompareVersion.compareVersions;

/**
 * Orchestrates version upload, patches, releases, checks, and execution.
 */
public class VersionManager {
    private final AppStore store;
    private final FileService files;
    private final DiffService diffs;
    private final InstallationService installationService;

    public VersionManager(AppStore store, FileService files, DiffService diffs, InstallationService installationService) {
        this.store = store;
        this.files = files;
        this.diffs = diffs;
        this.installationService = installationService;
    }

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
        String apkUrl = files.uploadFile(apkContent,"APK");
        AppVersion v = new AppVersion(version, minAndroidVersion, description, apkUrl);
        store.putVersion(v);
        return v;
    }

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
        String diffUrl = files.uploadFile(diffs.createDiffPack(fromApk, toApk),"DIFF");
        to.addDiffPack(fromVersion, diffUrl);
        System.out.println("[PATCH] Diff created between " + fromVersion + " -> " + toVersion);
        return diffUrl;
    }

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

    public boolean isAppVersionSupported(String targetVersion, Device device) {
        AppVersion v = store.getVersion(targetVersion);
        if (v == null || !store.isReleased(targetVersion)) return false;
        if (device.getAndroidVersion() < v.getMinAndroidVersion()) return false;

        return store.getRollout(targetVersion)
                .map(strategy -> strategy.isEligible(device))
                .orElse(false);
    }

    public Optional<UpdatePlan> checkForUpdates(Device device) {
        System.out.println("\n[CHECK] Checking updates for device: " + device);

        List<String> released = store.releasedVersionsSorted();
        if (released.isEmpty()) {
            System.out.println("[INFO] No released versions available.");
            return Optional.empty();
        }

        String current = device.getCurrentAppVersion();
        if (current == null) {
            System.out.println("[INFO] Device has no current version installed.");
        } else {
            System.out.println("[INFO] Current version: " + current);
        }

        System.out.println("[INFO] Released versions available: " + released);

        // ✅ Step 1: Filter out lower or equal versions
        List<String> newer = released.stream()
                .filter(v -> current == null || CompareVersion.compareVersions(v, current) > 0)
                .toList();

        if (newer.isEmpty()) {
            System.out.println("[RESULT] No newer versions available for device " + device.getDeviceId());
            return Optional.empty();
        }

        // ✅ Step 2: Pick the latest eligible version
        String latest = null;
        for (String version : newer) {
            if (isAppVersionSupported(version, device)) {
                latest = version; // keep last eligible
            } else {
                System.out.println("[SKIP] " + version + " → Not eligible (rollout/minAndroidVersion restriction).");
            }
        }

        if (latest == null) {
            System.out.println("[RESULT] No eligible updates for device " + device.getDeviceId());
            return Optional.empty();
        }

        AppVersion target = store.getVersion(latest);
        AppVersion currentApp = (current != null) ? store.getVersion(current) : null;

        // ✅ Step 3: Choose update type
        UpdatePlan plan;
        if (current == null) {
            plan = new UpdatePlan(UpdateType.INSTALL, null, target, target.getApkUrl(), null);
            System.out.println("[PLAN] Device has no app → Install " + latest);
        } else {
            String diffUrl = target.getDiffFrom(current);
            if (diffUrl != null) {
                plan = new UpdatePlan(UpdateType.UPDATE, currentApp, target, null, diffUrl);
                System.out.println("[PLAN] Found diff update from " + current + " → " + latest);
            } else {
                System.out.println("[INFO] No diff available between " + current + " → " + target.getVersion() + ". Attempting dynamic diff creation...");
                String generatedDiff = diffs.generateDiffIfMissing(store.getVersion(current), target);
                plan = new UpdatePlan(UpdateType.UPDATE, store.getVersion(current), target, null, generatedDiff);
                System.out.println("[PLAN] Found diff update from " + current + " → " + latest);
            }
        }

        System.out.println("[RESULT] Final update plan for " + device.getDeviceId() + ": " + plan);
        return Optional.of(plan);
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
                    installationService.installApp(device, plan.apkUrl());
                    device.setCurrentAppVersion(plan.target().getVersion());
                }
                case UPDATE -> {
                    installationService.updateApp(device, plan.diffUrl());
                    device.setCurrentAppVersion(plan.target().getVersion());
                }
            }
        }
    }

    private AppVersion getAppVersion(String version) {
        AppVersion v = store.getVersion(version);
        if (v == null) {
            System.out.println("[ERROR] Version does not exist in store: " + version);
            return null;
        }
        return v;
    }
}

