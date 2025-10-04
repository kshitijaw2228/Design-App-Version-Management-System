package org.phonepe.domain;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AppVersion {
    private final String version;
    private final int minAndroidVersion;
    private final String description;
    private final String apkUrl;
    private boolean released;
    private final Map<String, String> diffPacks = new ConcurrentHashMap<>();

    public AppVersion(String version, int minAndroidVersion, String description, String apkUrl) {
        this.version = Objects.requireNonNull(version);
        this.minAndroidVersion = minAndroidVersion;
        this.description = description == null ? "" : description;
        this.apkUrl = Objects.requireNonNull(apkUrl);
    }

    public String getVersion() { return version; }
    public int getMinAndroidVersion() { return minAndroidVersion; }
    public String getDescription() { return description; }
    public String getApkUrl() { return apkUrl; }
    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public void addDiffPack(String fromVersion, String diffUrl) {
        diffPacks.put(fromVersion, diffUrl);
    }

    public String getDiffFrom(String fromVersion) {
        return diffPacks.get(fromVersion);
    }

    @Override
    public String toString() {
        return "AppVersion{" +
                "version='" + version + '\'' +
                ", minAndroidVersion=" + minAndroidVersion +
                ", released=" + released +
                '}';
    }
}

