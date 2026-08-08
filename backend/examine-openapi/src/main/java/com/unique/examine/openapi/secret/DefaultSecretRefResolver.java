package com.unique.examine.openapi.secret;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultSecretRefResolver implements SecretRefResolver {
    private static final int MAXIMUM_SECRET_BYTES = 65_536;
    private final List<Path> fileRoots;

    public DefaultSecretRefResolver(List<Path> fileRoots) {
        var normalized = new ArrayList<Path>();
        for (var root : List.copyOf(fileRoots)) {
            if (root == null || !root.isAbsolute()) {
                throw new IllegalArgumentException("OpenAPI secret file roots must be absolute");
            }
            normalized.add(root.normalize());
        }
        this.fileRoots = List.copyOf(normalized);
    }

    @Override
    public Optional<byte[]> resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 512) {
            return Optional.empty();
        }
        if (secretRef.startsWith("env://")) {
            return environment(secretRef.substring("env://".length()));
        }
        if (secretRef.startsWith("file://")) {
            return file(secretRef);
        }
        return Optional.empty();
    }

    private static Optional<byte[]> environment(String name) {
        if (!name.matches("^[A-Z][A-Z0-9_]{0,127}$")) {
            return Optional.empty();
        }
        var value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length > MAXIMUM_SECRET_BYTES ? Optional.empty() : Optional.of(bytes);
    }

    private Optional<byte[]> file(String secretRef) {
        if (fileRoots.isEmpty()) {
            return Optional.empty();
        }
        try {
            var candidate = Path.of(URI.create(secretRef)).toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
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
            if (!allowed || Files.size(real) < 1 || Files.size(real) > MAXIMUM_SECRET_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(real));
        } catch (IllegalArgumentException | IOException | SecurityException exception) {
            return Optional.empty();
        }
    }
}
