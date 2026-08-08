package com.unique.examine.core.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves tenant-scoped secret references without exposing a provider or
 * storage implementation to feature modules.
 */
public interface SecretResolverFacade {
    Optional<ResolvedSecret> resolve(SecretRequest request);

    record SecretRequest(
            long systemId,
            long tenantId,
            String reference
    ) {
        public SecretRequest {
            if (systemId <= 0 || tenantId <= 0) {
                throw new IllegalArgumentException(
                        "Secret scope ids must be positive");
            }
            if (reference == null
                    || reference.isBlank()
                    || reference.length() > 512) {
                throw new IllegalArgumentException(
                        "Secret reference must contain 1 to 512 characters");
            }
            reference = reference.strip();
        }
    }

    /**
     * Mutable, closeable value so callers can clear their local plaintext
     * copy immediately after signing.
     */
    final class ResolvedSecret implements AutoCloseable {
        private byte[] value;

        public ResolvedSecret(byte[] value) {
            if (value == null || value.length == 0) {
                throw new IllegalArgumentException(
                        "Resolved secret must not be empty");
            }
            this.value = value.clone();
        }

        public static ResolvedSecret utf8(String value) {
            Objects.requireNonNull(value, "value");
            return new ResolvedSecret(
                    value.getBytes(StandardCharsets.UTF_8));
        }

        public synchronized byte[] copyBytes() {
            if (value == null) {
                throw new IllegalStateException(
                        "Resolved secret has already been cleared");
            }
            return value.clone();
        }

        @Override
        public synchronized void close() {
            if (value != null) {
                Arrays.fill(value, (byte) 0);
                value = null;
            }
        }

        @Override
        public String toString() {
            return "ResolvedSecret[redacted]";
        }
    }
}
