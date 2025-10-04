package org.phonepe;

import org.phonepe.domain.Device;
import org.phonepe.domain.UpdatePlan;
import org.phonepe.enums.UpdateType;
import org.phonepe.rollout.BetaRolloutStrategy;
import org.phonepe.service.DiffService;
import org.phonepe.service.FileService;
import org.phonepe.service.Installer;
import org.phonepe.service.VersionManager;
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

        // ---- Upload versions ----
        vm.uploadNewVersion("1.0.0", 24, "Initial release",
                "APK_v1".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("2.0.0", 26, "Big features",
                "APK_v2".getBytes(StandardCharsets.UTF_8));
        vm.uploadNewVersion("3.0.0", 26, "UI refresh",
                "APK_v3".getBytes(StandardCharsets.UTF_8));

        // ---- Create patches ----
        vm.createUpdatePatch("1.0.0", "2.0.0");
        vm.createUpdatePatch("2.0.0", "3.0.0");
        vm.createUpdatePatch("1.0.0", "3.0.0");

        // ---- Release with strategies ----
        vm.releaseVersion("2.0.0", new BetaRolloutStrategy(Set.of("Device-A", "Device-B"))); // only A,B
        // Simulate "full" rollout of 3.0.0 to the devices we test by whitelisting them
        vm.releaseVersion("3.0.0",
                new BetaRolloutStrategy(Set.of("Device-A", "Device-B", "Device-C", "Device-X", "Device-U", "Device-RACE", "Device-NEW")));

        // ---- Devices ----
        Device devA = new Device("Device-A", "Pixel-7", 34, "1.0.0");
        Device devB = new Device("Device-B", "Samsung-S23", 33, "2.0.0");
        Device devC = new Device("Device-C", "Moto-G", 26, "1.0.0");    // not in 2.0.0 beta; eligible for 3.0.0 beta
        Device devOld = new Device("Device-OLD", "Nexus-5x", 23, null); // below min api for 2.0.0+
        Device devNew = new Device("Device-NEW", "Pixel-8", 34, null); // fresh device


        // ---- Check/update sequence ----
        runFlow(vm, devA);
        runFlow(vm, devB);
        runFlow(vm, devC);
        runFlow(vm, devOld);
        runFlow(vm, devNew);

        // ---- Assertions (quick coverage) ----
        System.out.println("\n--- Assertions ---");
        assertTrue("Device A bumped to 3.0.0", "3.0.0".equals(devA.getCurrentAppVersion()));
        assertTrue("Device B bumped to 3.0.0", "3.0.0".equals(devB.getCurrentAppVersion()));
        assertTrue("Device C bumped to 3.0.0", "3.0.0".equals(devC.getCurrentAppVersion()));
        assertTrue("Device OLD stays null (min API not met)", devOld.getCurrentAppVersion() == null);

        // Unknown current version → full install of latest eligible (3.0.0)
        Device dUnknown = new Device("Device-U", "Test", 34, "1.2.9");
        Optional<UpdatePlan> planUnknown = vm.checkForUpdates(dUnknown);
        assertTrue("Unknown current => INSTALL to 3.0.0",
                planUnknown.isPresent()
                        && planUnknown.get().type() == UpdateType.INSTALL
                        && "3.0.0".equals(planUnknown.get().target().getVersion()));

        // (Optional) simple race demo: two threads try to update same device; end state should be latest once
        System.out.println("\n--- 2 threads try to update same device ---");
        CountDownLatch start = new CountDownLatch(1);
        final Object firstLock = new Object();
        final String[] winner = new String[1];
        Device dRace = new Device("Device-RACE", "Test", 34, "1.0.0");
        Runnable upd = () -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            vm.checkForUpdates(dRace).ifPresent(plan -> {
                vm.executeTask(dRace, plan); // assumes per-device locking inside VersionManager
                synchronized (firstLock) {
                    if (winner[0] == null) {
                        winner[0] = Thread.currentThread().getName();
                        System.out.println("Device updated on " + winner[0]);
                    }
                }
            });
        };

        Thread a = new Thread(upd,"Thread-a");
        Thread b = new Thread(upd, "Thread-b");
        a.start(); b.start();
        start.countDown(); // GO
        a.join(); b.join();

        System.out.println("[INFO] Race device final version = " + dRace.getCurrentAppVersion());

        System.out.println("\nAll done.");
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
