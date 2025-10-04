package org.phonepe;

import org.phonepe.domain.*;
import org.phonepe.enums.UpdateType;
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
        DiffService diffs = new DiffService();
        Installer installer = new Installer(files);
        VersionManager vm = new VersionManager(store, files, diffs, installer);

        setupBaseVersions(vm, store);
        testUpload(vm, store);
        testPatch(vm);
        testRelease(vm, store);
        testCheckForUpdates(vm);
        testEndToEndFlow(vm);
        testRaceCondition(vm);

        System.out.println("\n✅ ALL TESTS COMPLETED SUCCESSFULLY ✅");
    }

    // ---------------- TEST SECTIONS ----------------

    private static void setupBaseVersions(VersionManager vm, AppStore store) {
        vm.uploadNewVersion("3.1.2", 24, "Initial release", "APK_v1".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("3.4.1", 26, "Big features", "APK_v2".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("4.1.0", 26, "UI refresh", "APK_v3".getBytes(StandardCharsets.UTF_8));

        vm.createUpdatePatch("3.1.2", "3.4.1");
        vm.createUpdatePatch("3.4.1", "4.1.0");
        vm.createUpdatePatch("3.1.2", "4.1.0");

        vm.releaseVersion("4.1.0", new BetaRolloutStrategy(Set.of(
                "Device-A", "Device-B", "Device-C", "Device-X", "Device-U", "Device-RACE", "Device-NEW")));

        assertTrue("Base versions uploaded", store.allVersions().size() >= 3);
    }

    private static void testUpload(VersionManager vm, AppStore store) {
        System.out.println("\n=========== TEST: uploadNewVersion ===========");

        AppVersion v1 = vm.uploadNewVersion("6.0.0", 28, "Experimental build", "APK_v6".getBytes(StandardCharsets.UTF_8));
        assertTrue("Version 6.0.0 stored", v1 != null && store.getVersion("6.0.0") == v1);

        vm.uploadNewVersion("X.0.0", 0, "Bad upload", "APK".getBytes());
        vm.uploadNewVersion("3.1.2", 24, "Duplicate", "APK_dup".getBytes());
    }

    private static void testPatch(VersionManager vm) {
        System.out.println("\n=========== TEST: createUpdatePatch ===========");

        String diff1 = vm.createUpdatePatch("3.1.2", "3.4.1");
        assertTrue("Patch 3.1.2 → 3.4.1 created", diff1 != null && diff1.startsWith("mem://"));

        vm.createUpdatePatch("0.0.1", "3.4.1");  // invalid from
        vm.createUpdatePatch("3.4.1", "3.1.2");  // invalid order
    }

    private static void testRelease(VersionManager vm, AppStore store) {
        System.out.println("\n=========== TEST: releaseVersion ===========");

        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));
        assertTrue("3.4.1 released", store.isReleased("3.4.1"));

        vm.releaseVersion("9.9.9", new BetaRolloutStrategy(Set.of("Device-A"))); // invalid version
        vm.releaseVersion("3.4.1", null); // null strategy

        // re-release idempotent
        vm.releaseVersion("3.4.1", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));
        assertTrue("Re-release handled safely", store.isReleased("3.4.1"));
    }

    private static void testCheckForUpdates(VersionManager vm) {
        System.out.println("\n=========== TEST: checkForUpdates ===========");

        vm.uploadNewVersion("5.0.0", 26, "Major upgrade", "APK_v5".getBytes(StandardCharsets.UTF_8));
        vm.createUpdatePatch("4.1.0", "5.0.0");
        vm.releaseVersion("5.0.0", new BetaRolloutStrategy(Set.of("Device-A", "Device-B")));

        Device deviceA = new Device("Device-A", "Pixel-7", 34, "4.1.0");
        checkUpdate(vm, deviceA, true, "Device-A should update to 5.0.0");

        Device deviceB = new Device("Device-B", "Galaxy-S24", 34, "5.0.0");
        checkUpdate(vm, deviceB, false, "Device-B already latest");

        Device deviceC = new Device("Device-C", "Moto-G", 34, "4.1.0");
        checkUpdate(vm, deviceC, false, "Device-C not whitelisted");

        Device deviceOld = new Device("Device-OLD", "Nexus-5x", 23, "4.1.0");
        checkUpdate(vm, deviceOld, false, "Device-OLD below min supported");
    }

    private static void testEndToEndFlow(VersionManager vm) {
        System.out.println("\n=========== TEST: end-to-end flow ===========");
        Device[] devices = {
                new Device("Device-A", "Pixel-7", 34, "3.1.2"),
                new Device("Device-B", "Samsung-S23", 33, "3.4.1"),
                new Device("Device-C", "Moto-G", 26, "3.1.2"),
                new Device("Device-OLD", "Nexus-5x", 23, null),
                new Device("Device-NEW", "Pixel-8", 34, null)
        };

        for (Device d : devices) runFlow(vm, d);

        assertTrue("Device-A bumped to 4.1.0", "4.1.0".equals(devices[0].getCurrentAppVersion()));
        assertTrue("Device-B bumped to 4.1.0", "4.1.0".equals(devices[1].getCurrentAppVersion()));
        assertTrue("Device-C bumped to 4.1.0", "4.1.0".equals(devices[2].getCurrentAppVersion()));
        assertTrue("Device-OLD stays null", devices[3].getCurrentAppVersion() == null);

        Device dUnknown = new Device("Device-U", "Test", 34, "1.2.9");
        Optional<UpdatePlan> planUnknown = vm.checkForUpdates(dUnknown);
        assertTrue("Unknown current → INSTALL latest",
                planUnknown.isPresent()
                        && planUnknown.get().type() == UpdateType.INSTALL
                        && "4.1.0".equals(planUnknown.get().target().getVersion()));
    }

    private static void testRaceCondition(VersionManager vm) throws InterruptedException {
        System.out.println("\n=========== TEST: Race Condition ===========");

        Device dRace = new Device("Device-RACE", "Test", 34, "1.0.0");
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

    // ---------------- HELPERS ----------------

    private static void checkUpdate(VersionManager vm, Device d, boolean expectUpdate, String msg) {
        Optional<UpdatePlan> plan = vm.checkForUpdates(d);
        if (expectUpdate) {
            assertTrue(msg, plan.isPresent());
            plan.ifPresent(p -> System.out.println("Plan for " + d.getDeviceId() + ": " + p));
        } else {
            assertTrue(msg, plan.isEmpty());
        }
    }

    private static void runFlow(VersionManager vm, Device d) {
        System.out.println("\n--- Device: " + d.getDeviceId() + " ---");
        Optional<UpdatePlan> plan = vm.checkForUpdates(d);
        if (plan.isEmpty()) {
            System.out.println("No update available.");
            return;
        }
        System.out.println("Plan: " + plan.get());
        vm.executeTask(d, plan.get());
        System.out.println("After task, version = " + d.getCurrentAppVersion());
    }

    private static void assertTrue(String msg, boolean cond) {
        System.out.println(cond ? "[OK] " + msg : "[FAILED] " + msg);
    }
}
