package org.phonepe.service;

/**
 * Very naive diff pack creator.
 * For the exercise, we just join the two payloads to simulate a diff.
 */
public class DiffService {
    public byte[] createDiffPack(byte[] fromApk, byte[] toApk) {
        byte[] out = new byte[fromApk.length + toApk.length + 4];
        // simple header + concat; correctness is irrelevant for the round
        out[0] = 'D'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        System.arraycopy(fromApk, 0, out, 4, fromApk.length);
        System.arraycopy(toApk, 0, out, 4 + fromApk.length, toApk.length);
        return out;
    }
}

