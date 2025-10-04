package org.phonepe.store;

import org.phonepe.domain.AppVersion;
import org.phonepe.rollout.RolloutStrategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for versions and rollout strategies.
 */
public class AppStore {
    // version string -> AppVersion
    private final Map<String, AppVersion> versions = new ConcurrentHashMap<>();
    // version string -> rollout strategy (only for released versions)
    private final Map<String, RolloutStrategy> releases = new ConcurrentHashMap<>();

//    public void putVersion(AppVersion v) {
//        if (versions.containsKey(v.getVersion())) {
//            throw new IllegalArgumentException("Version already exists: " + v.getVersion());
//        }
//        versions.put(v.getVersion(), v);
//    }

    public void putVersion(AppVersion v) {
        AppVersion prev = versions.putIfAbsent(v.getVersion(), v);
        if (prev != null) throw new IllegalArgumentException("Version already exists: " + v.getVersion());
    }


    public AppVersion getVersion(String version) {
        return versions.get(version);
    }

    public Collection<AppVersion> allVersions() {
        return versions.values();
    }

    public void markReleased(String version, RolloutStrategy strategy) {
        AppVersion v = versions.get(version);
        if (v == null) throw new IllegalArgumentException("Unknown version: " + version);
        // publish-once semantics
        releases.putIfAbsent(version, strategy);
        v.setReleased(true); // visibility piggybacks on CHM write above
    }

    public Optional<RolloutStrategy> getRollout(String version) {
        return Optional.ofNullable(releases.get(version));
    }

    public boolean isReleased(String version) {
        return releases.containsKey(version);
    }

    public List<String> releasedVersionsSorted() {
        List<String> out = new ArrayList<>(releases.keySet());
        out.sort(AppStore::compareVersions); // uses the helper below
        return out;
    }

    private static int compareVersions(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? Integer.parseInt((as[i])) : 0;
            int bi = i < bs.length ? Integer.parseInt((bs[i])) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        // tie-breaker if numerically equal (e.g., "1.0" vs "1.0.0")
        return a.length() - b.length();
    }

    private static List<Integer> tokenize(String v) {
        List<Integer> parts = new ArrayList<>();
        for (String s : v.split("\\.")) {
            try { parts.add(Integer.parseInt(s)); } catch (NumberFormatException e) { parts.add(0); }
        }
        while (parts.size() < 3) parts.add(0);
        return parts;
    }
}

