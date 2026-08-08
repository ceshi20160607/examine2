package com.unique.examine.openapi.secret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSecretRefResolverTest {
    @TempDir
    Path directory;

    @Test
    void readsOnlyRegularFilesInsideConfiguredRoots() throws Exception {
        var secretFile = directory.resolve("client.secret");
        Files.writeString(secretFile, "not-plaintext-in-database");
        var resolver = new DefaultSecretRefResolver(List.of(directory.toAbsolutePath()));

        assertThat(resolver.resolve(secretFile.toUri().toString()))
                .hasValue("not-plaintext-in-database".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unsupportedAndOutOfRootReferencesFailClosed() throws Exception {
        var allowed = directory.resolve("allowed");
        var outside = directory.resolve("outside.secret");
        Files.createDirectory(allowed);
        Files.writeString(outside, "secret");
        var resolver = new DefaultSecretRefResolver(List.of(allowed.toAbsolutePath()));

        assertThat(resolver.resolve("vault://production/openapi")).isEmpty();
        assertThat(resolver.resolve("env://lower_case")).isEmpty();
        assertThat(resolver.resolve(outside.toUri().toString())).isEmpty();
    }
}
