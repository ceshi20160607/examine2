package com.unique.examine.module.runtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public final class FileSensitiveKeyProvider implements SensitiveKeyProvider {
    private static final String VERSION_PATTERN = "^[A-Za-z0-9._-]{1,64}$";

    private final ObjectMapper objectMapper;
    private final String configuredPath;

    public FileSensitiveKeyProvider(
            ObjectMapper objectMapper,
            @Value("${examine.runtime.sensitive.key-ring-file:}") String configuredPath
    ) {
        this.objectMapper = objectMapper;
        this.configuredPath = configuredPath == null ? "" : configuredPath.trim();
    }

    @Override
    public Optional<KeyRing> current() {
        if (configuredPath.isEmpty()) {
            return Optional.empty();
        }
        var path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            var document = objectMapper.readValue(Files.readAllBytes(path), KeyRingDocument.class);
            return Optional.of(validate(document));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static KeyRing validate(KeyRingDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Missing key ring");
        }
        var encryption = decode(document.encryptionKeys(), 32, "encryption");
        var hashes = decode(document.hashKeys(), 32, "hash");
        requireVersion(document.activeEncryptionKeyVersion(), encryption, "active encryption");
        requireVersion(document.activeHashKeyVersion(), hashes, "active hash");
        var queryVersions = document.queryHashKeyVersions() == null
                ? List.<String>of() : List.copyOf(document.queryHashKeyVersions());
        if (queryVersions.isEmpty() || !queryVersions.contains(document.activeHashKeyVersion())
                || queryVersions.stream().distinct().count() != queryVersions.size()) {
            throw new IllegalArgumentException("Invalid query hash versions");
        }
        queryVersions.forEach(version -> requireVersion(version, hashes, "query hash"));
        for (var encryptionKey : encryption.values()) {
            for (var hashKey : hashes.values()) {
                if (java.security.MessageDigest.isEqual(encryptionKey, hashKey)) {
                    throw new IllegalArgumentException("Encryption and hash keys must be independent");
                }
            }
        }
        return new KeyRing(document.activeEncryptionKeyVersion(), document.activeHashKeyVersion(),
                encryption, hashes, queryVersions);
    }

    private static Map<String, byte[]> decode(Map<String, String> encoded, int minimumBytes, String kind) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Missing " + kind + " keys");
        }
        var result = new LinkedHashMap<String, byte[]>();
        for (var entry : encoded.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches(VERSION_PATTERN) || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid " + kind + " key entry");
            }
            var decoded = Base64.getDecoder().decode(entry.getValue());
            if (decoded.length < minimumBytes || "encryption".equals(kind) && decoded.length != 32) {
                throw new IllegalArgumentException("Invalid " + kind + " key length");
            }
            result.put(entry.getKey(), decoded);
        }
        return Map.copyOf(result);
    }

    private static void requireVersion(String version, Map<String, byte[]> keys, String kind) {
        if (version == null || !version.matches(VERSION_PATTERN) || !keys.containsKey(version)) {
            throw new IllegalArgumentException("Missing " + kind + " key version");
        }
    }

    private record KeyRingDocument(
            String activeEncryptionKeyVersion,
            String activeHashKeyVersion,
            Map<String, String> encryptionKeys,
            Map<String, String> hashKeys,
            List<String> queryHashKeyVersions
    ) { }
}
