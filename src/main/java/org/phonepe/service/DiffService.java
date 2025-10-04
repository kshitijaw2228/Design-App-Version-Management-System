package org.phonepe.service;

import org.phonepe.domain.AppVersion;

/**
 * Very naive diff pack creator.
 * For the exercise, we just join the two payloads to simulate a diff.
 */
public class DiffService {

    private final FileService files;

    public DiffService(FileService files) {
        this.files = files;
    }

    public byte[] createDiffPack(byte[] fromApk, byte[] toApk) {
        byte[] out = new byte[fromApk.length + toApk.length + 4];
        // simple header + concat; correctness is irrelevant for the round
        out[0] = 'D'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        System.arraycopy(fromApk, 0, out, 4, fromApk.length);
        System.arraycopy(toApk, 0, out, 4 + fromApk.length, toApk.length);
        return out;
    }

    /**
     * Generate a diff if missing between two versions.
     */
    public String generateDiffIfMissing(AppVersion from, AppVersion to) {
        String existing = to.getDiffFrom(from.getVersion());
        if (existing != null) {
            System.out.println("[INFO] Reusing existing diff between " + from.getVersion() + " → " + to.getVersion());
            return existing;
        }

        byte[] fromApk = files.getFile(from.getApkUrl());
        byte[] toApk = files.getFile(to.getApkUrl());
        if (fromApk == null || toApk == null) {
            System.out.println("[WARN] One of the APKs is missing, cannot generate diff.");
            return null;
        }

        byte[] pack = createDiffPack(fromApk, toApk);
        String diffUrl = files.uploadFile(pack, "DIFF");
        to.addDiffPack(from.getVersion(), diffUrl);

        System.out.println("[DIFF] Created dynamic diff for " + from.getVersion() + " → " + to.getVersion() +
                " (" + pack.length + " bytes)");
        return diffUrl;
    }

}

