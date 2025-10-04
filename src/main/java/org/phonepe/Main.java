package org.phonepe;

import org.phonepe.domain.AppVersion;
import org.phonepe.domain.Device;
import org.phonepe.domain.UpdatePlan;
import org.phonepe.enums.UpdateType;
import org.phonepe.rollout.BetaRolloutStrategy;
import org.phonepe.service.*;
import org.phonepe.store.AppStore;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        AppStore store = new AppStore();
        FileService files = new FileService();
        DiffService diffs = new DiffService();
        Installer installer = new Installer(files);
        VersionManager vm = new VersionManager(store, files, diffs, installer);

        System.out.println("=========== TEST: uploadNewVersion ===========");
        // ✅ Happy path
        AppVersion v1 = vm.uploadNewVersion("3.1.2", 24, "Initial release",
                "APK_v1".getBytes(StandardCharsets.UTF_8));
        assertTrue("Version 3.1.2 stored", store.getVersion("3.1.2") == v1);
        assertTrue("APK uploaded to mem://", v1.getApkUrl().startsWith("mem://"));

        // ❌ Edge: Missing required field (min version)
        boolean failed = false;
        try {
            vm.uploadNewVersion("X.0.0", 0, "Bad upload", "APK".getBytes());
        } catch (AssertionError | IllegalArgumentException e) {
            failed = true;
        }
        assertTrue("Missing minVersion correctly rejected", failed);

        // ❌ Edge: duplicate version
        boolean dupFailed = false;
        try {
            vm.uploadNewVersion("3.1.2", 24, "Duplicate version", "APK_v1".getBytes());
        } catch (IllegalArgumentException e) {
            dupFailed = true;
        }
        assertTrue("Duplicate upload correctly rejected", dupFailed);

        // ---- Upload additional versions ----
        vm.uploadNewVersion("3.4.1", 26, "Big features",
                "APK_v2".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("4.1.0", 26, "UI refresh",
                "APK_v3".getBytes(StandardCharsets.UTF_8));

        System.out.println("\n=========== TEST: createUpdatePatch ===========");
        // ✅ Happy path
        String diff1 = vm.createUpdatePatch("3.1.2", "3.4.1");
        assertTrue("Patch 3.1.2 -> 3.4.1 created", diff1.startsWith("mem://"));

        // ❌ Edge: fromVersion doesn't exist
        boolean invalidFrom = false;
        try {
            vm.createUpdatePatch("0.0.1", "3.4.1");
        } catch (IllegalArgumentException e) {
            invalidFrom = true;
        }
        assertTrue("Invalid fromVersion handled", invalidFrom);

        // ❌ Edge: fromVersion >= toVersion
        boolean invalidOrder = false;
        try {
            vm.createUpdatePatch("3.4.1", "3.1.2");
        } catch (AssertionError e) {
            invalidOrder = true;
        }
        assertTrue("fromVersion >= toVersion rejected", invalidOrder);

        // ✅ Idempotent duplicate patch
        String diffAgain = vm.createUpdatePatch("3.1.2", "3.4.1");
        assertTrue("Duplicate patch returns existing diff", diff1.equals(diffAgain));

        // Additional patches
        vm.createUpdatePatch("3.4.1", "4.1.0");
        vm.createUpdatePatch("3.1.2", "4.1.0");

        System.out.println("\n=========== TEST: releaseVersion ===========");
        // ✅ Happy path: valid rollout
        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));
        assertTrue("3.4.1 released", store.isReleased("3.4.1"));

        // ❌ Edge: version doesn’t exist
        boolean badRelease = false;
        try {
            vm.releaseVersion("9.9.9", new BetaRolloutStrategy(Set.of("Device-A")));
        } catch (IllegalArgumentException e) {
            badRelease = true;
        }
        assertTrue("Release invalid version rejected", badRelease);

        // ❌ Edge: null rollout strategy
        boolean badStrategy = false;
        try {
            vm.releaseVersion("3.4.1", null);
        } catch (IllegalArgumentException e) {
            badStrategy = true;
        }
        assertTrue("Null rollout rejected", badStrategy);

        // ✅ Idempotent re-release
        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));
        assertTrue("Re-release handled idempotently", store.isReleased("3.4.1"));

        // Release for full rollout
        vm.releaseVersion("4.1.0", new BetaRolloutStrategy(
                Set.of("Device-A", "Device-B", "Device-C", "Device-X", "Device-U", "Device-RACE", "Device-NEW")));

        System.out.println("\n=========== TEST: end-to-end flow ===========");
        Device devA = new Device("Device-A", "Pixel-7", 34, "3.1.2");
        Device devB = new Device("Device-B", "Samsung-S23", 33, "3.4.1");
        Device devC = new Device("Device-C", "Moto-G", 26, "3.1.2");
        Device devOld = new Device("Device-OLD", "Nexus-5x", 23, null);
        Device devNew = new Device("Device-NEW", "Pixel-8", 34, null);

        runFlow(vm, devA);
        runFlow(vm, devB);
        runFlow(vm, devC);
        runFlow(vm, devOld);
        runFlow(vm, devNew);

        System.out.println("\n--- Assertions ---");
        assertTrue("Device A bumped to 4.1.0", "4.1.0".equals(devA.getCurrentAppVersion()));
        assertTrue("Device B bumped to 4.1.0", "4.1.0".equals(devB.getCurrentAppVersion()));
        assertTrue("Device C bumped to 4.1.0", "4.1.0".equals(devC.getCurrentAppVersion()));
        assertTrue("Device OLD stays null (min API not met)", devOld.getCurrentAppVersion() == null);

        Device dUnknown = new Device("Device-U", "Test", 34, "1.2.9");
        Optional<UpdatePlan> planUnknown = vm.checkForUpdates(dUnknown);
        assertTrue("Unknown current => INSTALL to 4.1.0",
                planUnknown.isPresent()
                        && planUnknown.get().type() == UpdateType.INSTALL
                        && "4.1.0".equals(planUnknown.get().target().getVersion()));

        System.out.println("\n--- Race condition demo ---");
        CountDownLatch start = new CountDownLatch(1);
        final Object lock = new Object();
        final String[] winner = new String[1];
        Device dRace = new Device("Device-RACE", "Test", 34, "1.0.0");
        Runnable upd = () -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            vm.checkForUpdates(dRace).ifPresent(plan -> {
                vm.executeTask(dRace, plan);
                synchronized (lock) {
                    if (winner[0] == null) {
                        winner[0] = Thread.currentThread().getName();
                        System.out.println("Device updated on " + winner[0]);
                    }
                }
            });
        };

        Thread a = new Thread(upd, "Thread-A");
        Thread b = new Thread(upd, "Thread-B");
        a.start(); b.start();
        start.countDown();
        a.join(); b.join();
        System.out.println("[INFO] Race device final version = " + dRace.getCurrentAppVersion());
        assertTrue("Race device reached latest version", "4.1.0".equals(dRace.getCurrentAppVersion()));

        System.out.println("\n✅ ALL TESTS PASSED SUCCESSFULLY ✅");
    }

    private static void runFlow(VersionManager vm, Device device) {
        System.out.println("\n--- Device: " + device.getDeviceId() + " ---");
        Optional<UpdatePlan> planOpt = vm.checkForUpdates(device);
        if (planOpt.isEmpty()) {
            System.out.println("No update available.");
            return;
        }
        UpdatePlan plan = planOpt.get();
        System.out.println("Plan: " + plan);
        vm.executeTask(device, plan);
        System.out.println("After task, device version = " + device.getCurrentAppVersion());
    }

    private static void assertTrue(String msg, boolean cond) {
        if (!cond) throw new AssertionError("FAILED: " + msg);
        System.out.println("[OK] " + msg);
    }
}
