package org.phonepe.domain;

public class Device {
    private final String deviceId;
    private final String model;
    private final int androidVersion;
    private String currentAppVersion;
    private final Object lock = new Object();
    public Object lock() { return lock; }

    public Device(String deviceId, String model, int androidVersion, String currentAppVersion) {
        this.deviceId = deviceId;
        this.model = model;
        this.androidVersion = androidVersion;
        this.currentAppVersion = currentAppVersion;
    }

    public String getDeviceId() { return deviceId; }
    public String getModel() { return model; }
    public int getAndroidVersion() { return androidVersion; }
    public String getCurrentAppVersion() { return currentAppVersion; }
    public void setCurrentAppVersion(String ver) { this.currentAppVersion = ver; }

    @Override
    public String toString() {
        return "Device{" +
                "deviceId='" + deviceId + '\'' +
                ", model='" + model + '\'' +
                ", api=" + androidVersion +
                ", currentAppVersion='" + currentAppVersion + '\'' +
                '}';
    }
}

