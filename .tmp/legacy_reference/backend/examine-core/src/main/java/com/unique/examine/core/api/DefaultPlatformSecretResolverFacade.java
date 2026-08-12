package com.unique.examine.core.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Safe platform-scoped resolver for {@code env://NAME} and explicitly rooted
 * {@code file://} references. It shares the production reference resolver
 * without manufacturing tenant or system identifiers.
 */
public final class DefaultPlatformSecretResolverFacade
        implements PlatformSecretResolverFacade {
    private final DefaultSecretResolverFacade references;

    public DefaultPlatformSecretResolverFacade(List<Path> fileRoots) {
        this.references = new DefaultSecretResolverFacade(fileRoots);
    }

    DefaultPlatformSecretResolverFacade(
            List<Path> fileRoots,
            Function<String, String> environment
    ) {
        this.references = new DefaultSecretResolverFacade(
                fileRoots, environment);
    }

    @Override
    public Optional<SecretResolverFacade.ResolvedSecret> resolve(
            SecretRequest request
    ) {
        return references.resolveReference(request.reference());
    }
}
