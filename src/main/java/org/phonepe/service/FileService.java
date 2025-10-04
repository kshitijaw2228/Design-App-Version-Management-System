package org.phonepe.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory file store.
 * uploadFile -> returns "mem://<id>"
 * getFile    -> returns stored content
 */
public class FileService {
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    public String uploadFile(byte[] content, String fileType) {
        if (content == null || content.length == 0) {
            System.out.println("[ERROR] Cannot upload empty " + fileType + " file.");
            return null;
        }
        String id = "mem://" + UUID.randomUUID();
        storage.put(id, content);
        System.out.println("[FILE] Uploaded " + fileType + " file to " + id + " (" + content.length + " bytes)");
        return id;
    }

    public byte[] getFile(String url) {
        byte[] b = storage.get(url);
        if (b == null) {
            System.out.println("[ERROR] File not found in FileService: " + url);
            return null;
        }
        return b;
    }
}

