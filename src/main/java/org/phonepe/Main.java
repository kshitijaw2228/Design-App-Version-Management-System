package org.phonepe;

import org.phonepe.domain.*;
import org.phonepe.rollout.BetaRolloutStrategy;
import org.phonepe.service.*;
import org.phonepe.store.AppStore;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        AppStore store = new AppStore();
        FileService files = new FileService();
        DiffService diffs = new DiffService(files);
        InstallationService installationService = new InstallationService(files);
        VersionManager vm = new VersionManager(store, files, diffs, installationService);

        setupBaseVersions(vm, store);
        testUpload(vm, store);
        testPatch(vm);
        testRelease(vm, store);
        testCheckForUpdates(vm);
        testRaceCondition(vm);

        System.out.println("\n✅ ALL TESTS COMPLETED SUCCESSFULLY ✅");
    }

    // ---------------- TEST SECTIONS ----------------

    private static void setupBaseVersions(VersionManager vm, AppStore store) {

        System.out.println("\n=========== Setting up versions, patches and a release ===========");
        vm.uploadNewVersion("3.1.2", 24, "Initial release", "APK_v1".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("3.4.1", 26, "Big features", "APK_v2".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("4.1.0", 26, "UI refresh", "APK_v3".getBytes(StandardCharsets.UTF_8));

        vm.createUpdatePatch("3.1.2", "3.4.1");
        vm.createUpdatePatch("3.4.1", "4.1.0");

        vm.releaseVersion("4.1.0", new BetaRolloutStrategy(Set.of(
                "Device-A", "Device-B", "Device-C", "Device-X", "Device-U", "Device-RACE", "Device-NEW")));
    }

    private static void testUpload(VersionManager vm, AppStore store) {
        System.out.println("\n=========== TEST: uploadNewVersion ===========");
        System.out.println("\n--- Scenario 1: Successful upload ---");
        AppVersion v1 = vm.uploadNewVersion("6.0.0", 28, "Experimental build", "APK_v6".getBytes(StandardCharsets.UTF_8));
        System.out.println("\n--- Scenario 2: Upload with invalid version ---");
        vm.uploadNewVersion("X.0.0", 0, "Bad upload", "APK".getBytes());
        System.out.println("\n--- Scenario 3: Uploading duplicate version ---");
        vm.uploadNewVersion("3.1.2", 24, "Duplicate", "APK_dup".getBytes());
    }

    private static void testPatch(VersionManager vm) {
        System.out.println("\n=========== TEST: createUpdatePatch ===========");

        System.out.println("\n--- Scenario 1: Successful Patch creation ---");
        vm.createUpdatePatch("3.1.2", "4.1.0");

        System.out.println("\n--- Scenario 2: Duplicate Patch ---");
        vm.createUpdatePatch("3.1.2", "3.4.1");

        System.out.println("\n--- Scenario 3: Current Version does not exist ---");
        vm.createUpdatePatch("0.0.1", "3.4.1");

        System.out.println("\n--- Scenario 4: Current version > Target Version ---");
        vm.createUpdatePatch("3.4.1", "3.1.2");
    }

    private static void testRelease(VersionManager vm, AppStore store) {
        System.out.println("\n=========== TEST: releaseVersion ===========");

        System.out.println("\n--- Scenario 1: A version from store is released ---");
        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));

        System.out.println("\n--- Scenario 2: Non-existing version is released ---");
        vm.releaseVersion("9.9.9", new BetaRolloutStrategy(Set.of("Device-A")));

        System.out.println("\n--- Scenario 3: Invalid Parameters ---");
        vm.releaseVersion("3.4.1", null);

        System.out.println("\n--- Scenario 4: Re-releasing released version ---");
        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));
    }

    private static void testCheckForUpdates(VersionManager vm) {
        System.out.println("\n=========== TEST: checkForUpdates ===========");

        System.out.println("\n--- Adding one more version 5.0.0 to store ---");
        vm.uploadNewVersion("5.0.0", 26, "Major upgrade", "APK_v5".getBytes(StandardCharsets.UTF_8));
        vm.createUpdatePatch("4.1.0", "5.0.0");
        vm.releaseVersion("5.0.0", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));

        System.out.println("\n--- Scenario 1: Device-A is eligible and on older version ---");
        Device deviceA = new Device("Device-A", "Pixel-7", 34, "4.1.0");
        vm.checkForUpdates(deviceA);

        System.out.println("\n--- Scenario 2: Device-B is already on latest version ---");
        Device deviceB = new Device("Device-B", "Galaxy-S24", 34, "5.0.0");
        vm.checkForUpdates(deviceB);

        System.out.println("\n--- Scenario 3: Device-C is not part of beta rollout ---");
        Device deviceC = new Device("Device-C", "Moto-G", 34, "4.1.0");
        vm.checkForUpdates(deviceC);

        System.out.println("\n--- Scenario 4: Multiple versions available ---");
        Device deviceD = new Device("Device-D", "Pixel-9", 34, "3.4.1");
        vm.checkForUpdates(deviceD);

        System.out.println("\n--- Scenario 5: Device-OLD has API 23 (below minAndroidVersion 26) ---");
        Device deviceOld = new Device("Device-OLD", "Nexus-5x", 23, "4.1.0");
        vm.checkForUpdates(deviceOld);
    }

    private static void testRaceCondition(VersionManager vm) throws InterruptedException {
        System.out.println("\n=========== TEST: Race Condition ===========");

        Device dRace = new Device("Device-RACE", "Test", 34, "3.4.1");
        CountDownLatch start = new CountDownLatch(1);
        Object lock = new Object();
        final String[] winner = new String[1];

        Runnable upd = () -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            vm.checkForUpdates(dRace).ifPresent(plan -> {
                vm.executeTask(dRace, plan);
                synchronized (lock) {
                    if (winner[0] == null) {
                        winner[0] = Thread.currentThread().getName();
                        System.out.println("Device updated by " + winner[0]);
                    }
                }
            });
        };

        new Thread(upd, "Thread-A").start();
        new Thread(upd, "Thread-B").start();
        start.countDown();
        Thread.sleep(500);
        System.out.println("[INFO] Race device final version = " + dRace.getCurrentAppVersion());
    }

}
