package com.unique.examine.openapi.secret;

import java.util.Optional;

public interface SecretRefResolver {
    Optional<byte[]> resolve(String secretRef);
}
