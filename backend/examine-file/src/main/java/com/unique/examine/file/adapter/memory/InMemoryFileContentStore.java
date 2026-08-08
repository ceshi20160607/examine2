package com.unique.examine.file.adapter.memory;

import com.unique.examine.file.port.FileContentStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFileContentStore implements FileContentStore {
    private final ConcurrentHashMap<String, byte[]> content = new ConcurrentHashMap<>();

    @Override
    public void put(String objectKey, byte[] bytes) {
        if (objectKey == null || objectKey.isBlank() || bytes == null) {
            throw new IllegalArgumentException("Object key and content are required");
        }
        if (content.putIfAbsent(objectKey, bytes.clone()) != null) {
            throw new IllegalStateException("Object key already exists");
        }
    }

    @Override
    public Optional<byte[]> read(String objectKey) {
        var bytes = content.get(objectKey);
        return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
    }

    @Override
    public void delete(String objectKey) {
        content.remove(objectKey);
    }
}
