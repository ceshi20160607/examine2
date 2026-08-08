package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiCallbackSignerTest {
    @Test
    void producesStableVersionedHmacOverCanonicalEnvelope() {
        var payload = "{\"id\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        var hash = OpenApiCallbackSigner.payloadHash(payload);
        var signature = OpenApiCallbackSigner.sign(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "1760000000", 42, "RECORD_CREATED", hash);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(signature).startsWith("v1=").hasSize(67);
        assertThat(signature).isEqualTo(OpenApiCallbackSigner.sign(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "1760000000", 42, "RECORD_CREATED", hash));
    }
}
