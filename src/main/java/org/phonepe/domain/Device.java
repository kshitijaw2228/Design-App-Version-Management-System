package org.phonepe.domain;

public class Device {
    private final String deviceId;
    private final String model;          // e.g., "Pixel 7"
    private final int androidApi;        // e.g., 34
    private String currentAppVersion;
    // in Device
    private final Object lock = new Object();
    public Object lock() { return lock; }
// e.g., "1.0.0" (null if app not installed)

    public Device(String deviceId, String model, int androidApi, String currentAppVersion) {
        this.deviceId = deviceId;
        this.model = model;
        this.androidApi = androidApi;
        this.currentAppVersion = currentAppVersion;
    }

    public String getDeviceId() { return deviceId; }
    public String getModel() { return model; }
    public int getAndroidApi() { return androidApi; }
    public String getCurrentAppVersion() { return currentAppVersion; }
    public void setCurrentAppVersion(String ver) { this.currentAppVersion = ver; }

    @Override
    public String toString() {
        return "Device{" +
                "deviceId='" + deviceId + '\'' +
                ", model='" + model + '\'' +
                ", api=" + androidApi +
                ", currentAppVersion='" + currentAppVersion + '\'' +
                '}';
    }
}

