package com.unique.examine.core.api;

import java.util.Optional;

/** Resolves a platform-scoped SecretRef without forged system or tenant ids. */
public interface PlatformSecretResolverFacade {

    Optional<SecretResolverFacade.ResolvedSecret> resolve(SecretRequest request);

    record SecretRequest(String reference) {
        public SecretRequest {
            if (reference == null || reference.isBlank() || reference.length() > 512) {
                throw new IllegalArgumentException(
                        "Secret reference must contain 1 to 512 characters");
            }
            reference = reference.strip();
        }
    }
}
