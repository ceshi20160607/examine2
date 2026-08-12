package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSecretResolverFacadeTest {
    @TempDir
    Path directory;

    @Test
    void resolvesBoundedEnvironmentReferencesAndClearsLocalValue() {
        var resolver = new DefaultSecretResolverFacade(
                List.of(), Map.of("FLOW_SIGNING_KEY", "secret")::get);

        var resolved = resolver.resolve(request(
                "env://FLOW_SIGNING_KEY")).orElseThrow();

        assertThat(new String(
                resolved.copyBytes(), StandardCharsets.UTF_8))
                .isEqualTo("secret");
        assertThat(resolved.toString()).doesNotContain("secret");
        resolved.close();
        assertThatThrownByCopy(resolved);
        assertThat(resolver.resolve(request("env://lowercase"))).isEmpty();
    }

    @Test
    void fileReferencesAreRestrictedToConfiguredRealRootsAndSize() throws Exception {
        var allowed = Files.createDirectories(directory.resolve("allowed"));
        var outside = Files.createDirectories(directory.resolve("outside"));
        var secret = Files.writeString(
                allowed.resolve("signing.key"), "file-secret");
        var denied = Files.writeString(
                outside.resolve("denied.key"), "denied");
        var oversized = Files.write(
                allowed.resolve("oversized.key"),
                new byte[DefaultSecretResolverFacade.MAXIMUM_SECRET_BYTES + 1]);
        var resolver = new DefaultSecretResolverFacade(List.of(allowed));

        try (var value = resolver.resolve(request(
                secret.toUri().toString())).orElseThrow()) {
            assertThat(new String(
                    value.copyBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("file-secret");
        }
        assertThat(resolver.resolve(request(denied.toUri().toString())))
                .isEmpty();
        assertThat(resolver.resolve(request(oversized.toUri().toString())))
                .isEmpty();
    }

    private static SecretResolverFacade.SecretRequest request(
            String reference
    ) {
        return new SecretResolverFacade.SecretRequest(1, 2, reference);
    }

    private static void assertThatThrownByCopy(
            SecretResolverFacade.ResolvedSecret value
    ) {
        org.assertj.core.api.Assertions.assertThatThrownBy(value::copyBytes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cleared");
    }
}
