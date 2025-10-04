package org.phonepe.service;

import org.phonepe.domain.Device;

public class Installer {
    private final FileService files;

    public Installer(FileService files) { this.files = files; }

    public void installApp(Device device, String apkUrl) {
        // simulate device flashing
        byte[] apk = files.getFile(apkUrl);
        System.out.println("[INSTALL] device=" + device.getDeviceId()
                + " bytes=" + apk.length + " from=" + apkUrl);
    }

    public void updateApp(Device device, String diffUrl) {
        byte[] pack = files.getFile(diffUrl);
        System.out.println("[UPDATE] device=" + device.getDeviceId()
                + " bytes=" + pack.length + " from=" + diffUrl);
    }
}

