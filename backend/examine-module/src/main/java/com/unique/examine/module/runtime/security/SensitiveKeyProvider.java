package com.unique.examine.module.runtime.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SensitiveKeyProvider {
    Optional<KeyRing> current();

    default boolean available() {
        return current().isPresent();
    }

    record KeyRing(
            String activeEncryptionVersion,
            String activeHashVersion,
            Map<String, byte[]> encryptionKeys,
            Map<String, byte[]> hashKeys,
            List<String> queryHashVersions
    ) {
        public KeyRing {
            encryptionKeys = copy(encryptionKeys);
            hashKeys = copy(hashKeys);
            queryHashVersions = List.copyOf(queryHashVersions);
        }

        @Override
        public Map<String, byte[]> encryptionKeys() {
            return copy(encryptionKeys);
        }

        @Override
        public Map<String, byte[]> hashKeys() {
            return copy(hashKeys);
        }

        private static Map<String, byte[]> copy(Map<String, byte[]> values) {
            return values.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> entry.getValue().clone()));
        }
    }
}
