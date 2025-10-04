package org.phonepe.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory file store.
 * uploadFile -> returns "mem://<id>"
 * getFile    -> returns stored content
 */
public class FileService {
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    public String uploadFile(byte[] content) {
        String id = "mem://" + UUID.randomUUID();
        storage.put(id, content);
        return id;
    }

    public byte[] getFile(String url) {
        byte[] b = storage.get(url);
        if (b == null) throw new IllegalArgumentException("file not found: " + url);
        return b;
    }
}

