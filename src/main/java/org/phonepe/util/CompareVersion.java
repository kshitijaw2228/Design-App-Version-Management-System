package org.phonepe.util;

public class CompareVersion {
    //
    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? Integer.parseInt((as[i])) : 0;
            int bi = i < bs.length ? Integer.parseInt((bs[i])) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return a.length() - b.length();
    }
}
