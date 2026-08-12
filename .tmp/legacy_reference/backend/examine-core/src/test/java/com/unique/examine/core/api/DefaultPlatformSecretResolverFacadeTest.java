package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPlatformSecretResolverFacadeTest {
    @TempDir
    Path directory;

    @Test
    void resolvesPlatformEnvironmentReferenceWithoutScopeIds() {
        var resolver = new DefaultPlatformSecretResolverFacade(
                List.of(), Map.of("PLATFORM_AI_KEY", "platform-secret")::get);

        try (var value = resolver.resolve(request(
                "env://PLATFORM_AI_KEY")).orElseThrow()) {
            assertThat(new String(value.copyBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("platform-secret");
            assertThat(value.toString()).doesNotContain("platform-secret");
        }

        assertThat(resolver.resolve(request("env://lowercase"))).isEmpty();
    }

    @Test
    void restrictsPlatformFileReferenceToConfiguredRealRootAndSize()
            throws Exception {
        var allowed = Files.createDirectories(directory.resolve("allowed"));
        var outside = Files.createDirectories(directory.resolve("outside"));
        var secret = Files.writeString(
                allowed.resolve("platform-ai.key"), "file-secret");
        var denied = Files.writeString(outside.resolve("denied.key"), "denied");
        var oversized = Files.write(
                allowed.resolve("oversized.key"),
                new byte[DefaultSecretResolverFacade.MAXIMUM_SECRET_BYTES + 1]);
        var resolver = new DefaultPlatformSecretResolverFacade(List.of(allowed));

        try (var value = resolver.resolve(request(
                secret.toUri().toString())).orElseThrow()) {
            assertThat(new String(value.copyBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("file-secret");
        }
        assertThat(resolver.resolve(request(denied.toUri().toString())))
                .isEmpty();
        assertThat(resolver.resolve(request(oversized.toUri().toString())))
                .isEmpty();
    }

    private static PlatformSecretResolverFacade.SecretRequest request(
            String reference
    ) {
        return new PlatformSecretResolverFacade.SecretRequest(reference);
    }
}
