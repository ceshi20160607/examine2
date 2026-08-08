package com.unique.examine.file.domain;

import com.unique.examine.core.api.AggregateRef;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record FileAsset(
        long id,
        long systemId,
        long tenantId,
        long uploaderMemberId,
        String objectKey,
        String originalName,
        String mediaType,
        long size,
        String sha256,
        Instant createdAt,
        Map<AggregateRef, FileReference> references,
        long version
) {
    public FileAsset {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || uploaderMemberId <= 0) {
            throw new IllegalArgumentException("File identity and scope values must be positive");
        }
        objectKey = required(objectKey, "object key", 300);
        originalName = required(originalName, "original name", 255);
        mediaType = required(mediaType, "media type", 150);
        sha256 = required(sha256, "sha256", 64);
        if (size < 0 || createdAt == null || version <= 0) {
            throw new IllegalArgumentException("File metadata is incomplete");
        }
        references = references == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(references));
    }

    public FileAsset addReference(AggregateRef target, long memberId, Instant now) {
        if (references.containsKey(target)) {
            return this;
        }
        var updated = new LinkedHashMap<>(references);
        updated.put(target, new FileReference(target, memberId, now));
        return copy(updated);
    }

    public FileAsset removeReference(AggregateRef target) {
        if (!references.containsKey(target)) {
            return this;
        }
        var updated = new LinkedHashMap<>(references);
        updated.remove(target);
        return copy(updated);
    }

    public int referenceCount() {
        return references.size();
    }

    private FileAsset copy(Map<AggregateRef, FileReference> updatedReferences) {
        return new FileAsset(id, systemId, tenantId, uploaderMemberId, objectKey, originalName,
                mediaType, size, sha256, createdAt, updatedReferences, version + 1);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("File " + name + " must contain 1 to " + maxLength + " characters");
        }
        return value.trim();
    }
}
