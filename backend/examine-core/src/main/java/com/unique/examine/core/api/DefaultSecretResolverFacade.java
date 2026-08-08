package com.unique.examine.core.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Safe production resolver for {@code env://NAME} and explicitly rooted
 * {@code file://} references.
 */
public final class DefaultSecretResolverFacade
        implements SecretResolverFacade {
    static final int MAXIMUM_SECRET_BYTES = 65_536;

    private final List<Path> fileRoots;
    private final Function<String, String> environment;

    public DefaultSecretResolverFacade(List<Path> fileRoots) {
        this(fileRoots, System::getenv);
    }

    public DefaultSecretResolverFacade(
            List<Path> fileRoots,
            Function<String, String> environment
    ) {
        var normalized = new ArrayList<Path>();
        for (var root : List.copyOf(fileRoots)) {
            if (root == null || !root.isAbsolute()) {
                throw new IllegalArgumentException(
                        "Secret file roots must be absolute");
            }
            normalized.add(root.normalize());
        }
        this.fileRoots = List.copyOf(normalized);
        this.environment = java.util.Objects.requireNonNull(
                environment, "environment");
    }

    @Override
    public Optional<ResolvedSecret> resolve(SecretRequest request) {
        return resolveReference(request.reference());
    }

    Optional<ResolvedSecret> resolveReference(String reference) {
        if (reference.startsWith("env://")) {
            return environment(reference.substring("env://".length()));
        }
        if (reference.startsWith("file://")) {
            return file(reference);
        }
        return Optional.empty();
    }

    private Optional<ResolvedSecret> environment(String name) {
        if (!name.matches("^[A-Z][A-Z0-9_]{0,127}$")) {
            return Optional.empty();
        }
        try {
            var value = environment.apply(name);
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            return bytes.length > MAXIMUM_SECRET_BYTES
                    ? Optional.empty()
                    : Optional.of(new ResolvedSecret(bytes));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<ResolvedSecret> file(String reference) {
        if (fileRoots.isEmpty()) {
            return Optional.empty();
        }
        try {
            var candidate = Path.of(URI.create(reference))
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(
                    candidate, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            var real = candidate.toRealPath();
            var allowed = false;
            for (var root : fileRoots) {
                try {
                    if (real.startsWith(root.toRealPath())) {
                        allowed = true;
                        break;
                    }
                } catch (IOException | SecurityException ignored) {
                    // An unavailable configured root cannot authorize a secret.
                }
            }
            var size = Files.size(real);
            if (!allowed
                    || size < 1
                    || size > MAXIMUM_SECRET_BYTES) {
                return Optional.empty();
            }
            return Optional.of(
                    new ResolvedSecret(Files.readAllBytes(real)));
        } catch (IllegalArgumentException
                 | IOException
                 | SecurityException exception) {
            return Optional.empty();
        }
    }
}
