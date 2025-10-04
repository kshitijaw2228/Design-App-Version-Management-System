package org.phonepe.service;

import org.phonepe.domain.Device;

public class Installer {
    private final FileService files;

    public Installer(FileService files) { this.files = files; }

    public void installApp(Device device, String apkUrl) {
        byte[] apk = files.getFile(apkUrl);
        if (apk == null) {
            System.out.println("[ERROR] APK not found for install on " + device.getDeviceId());
            return;
        }
        System.out.println("[INSTALL] device=" + device.getDeviceId() +
                " bytes=" + apk.length + " from=" + apkUrl);
    }

    public void updateApp(Device device, String diffUrl) {
        byte[] pack = files.getFile(diffUrl);
        if (pack == null) {
            System.out.println("[ERROR] Diff pack not found for update on " + device.getDeviceId());
            return;
        }
        System.out.println("[UPDATE] device=" + device.getDeviceId() +
                " bytes=" + pack.length + " from=" + diffUrl);
    }
}

