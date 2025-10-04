package org.phonepe.store;

import org.phonepe.domain.AppVersion;
import org.phonepe.rollout.RolloutStrategy;
import org.phonepe.util.CompareVersion;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for versions and rollout strategies.
 */
public class AppStore {

    private final Map<String, AppVersion> versions = new ConcurrentHashMap<>();
    private final Map<String, RolloutStrategy> releases = new ConcurrentHashMap<>();

    public void putVersion(AppVersion v) {
        if (versions.containsKey(v.getVersion())) {
            System.out.println("[WARN] Version already exists in store: " + v.getVersion());
            return;
        }
        versions.put(v.getVersion(), v);
        System.out.println("[STORE] Version added: " + v.getVersion());
    }

    public AppVersion getVersion(String version) {
        return versions.get(version);
    }

    public void markReleased(String version, RolloutStrategy strategy) {
        AppVersion v = versions.get(version);
        if (v == null) {
            System.out.println("[ERROR] Cannot mark release — version not found: " + version);
            return;
        }
        if (strategy == null) {
            System.out.println("[ERROR] Rollout strategy is null for version: " + version);
            return;
        }
        releases.put(version, strategy);
        v.setReleased(true);
        System.out.println("[RELEASE] Version " + version + " released with strategy " + strategy.name());
    }

    public Optional<RolloutStrategy> getRollout(String version) {
        return Optional.ofNullable(releases.get(version));
    }

    public boolean isReleased(String version) {
        return releases.containsKey(version);
    }

    public List<String> releasedVersionsSorted() {
        List<String> out = new ArrayList<>(releases.keySet());
        out.sort(CompareVersion::compareVersions);
        return out;
    }

}

