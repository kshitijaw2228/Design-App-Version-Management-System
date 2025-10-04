package org.phonepe.domain;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AppVersion {
    private final String version;                   // e.g., "1.0.0"
    private final int minAndroidApi;                // e.g., 24
    private final String description;
    private final String apkUrl;                    // uploaded URL in our in-memory store
    private boolean released;                       // set true after release
    // fromVersion -> diffPackUrl (for updates)
    private final Map<String, String> diffPacks = new ConcurrentHashMap<>();

    public AppVersion(String version, int minAndroidApi, String description, String apkUrl) {
        this.version = Objects.requireNonNull(version);
        this.minAndroidApi = minAndroidApi;
        this.description = description == null ? "" : description;
        this.apkUrl = Objects.requireNonNull(apkUrl);
    }

    public String getVersion() { return version; }
    public int getMinAndroidApi() { return minAndroidApi; }
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

    public String putDiffIfAbsent(String fromVersion, String url) {
        String prev = diffPacks.putIfAbsent(fromVersion, url);
        return prev == null ? url : prev; // return the winner
    }

    @Override
    public String toString() {
        return "AppVersion{" +
                "version='" + version + '\'' +
                ", minAndroidApi=" + minAndroidApi +
                ", released=" + released +
                '}';
    }
}

