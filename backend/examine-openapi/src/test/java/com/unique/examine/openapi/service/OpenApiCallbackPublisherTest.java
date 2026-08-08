package com.unique.examine.openapi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiCallbackPublisherTest {
    @Test
    void deterministicIdentityIsBoundedStableAndLengthDelimited() {
        var first = OpenApiCallbackPublisher.deterministicEventId(
                "RECORD_CREATED", "ab", "c", "x".repeat(4000));
        var replay = OpenApiCallbackPublisher.deterministicEventId(
                "RECORD_CREATED", "ab", "c", "x".repeat(4000));
        var ambiguousWithoutLengths = OpenApiCallbackPublisher.deterministicEventId(
                "RECORD_CREATED", "a", "bc", "x".repeat(4000));

        assertThat(first).isEqualTo(replay)
                .matches("RECORD_CREATED:[0-9a-f]{64}")
                .hasSizeLessThanOrEqualTo(160)
                .isNotEqualTo(ambiguousWithoutLengths);
        assertThatThrownBy(() -> OpenApiCallbackPublisher.deterministicEventId(
                "RECORD_CREATED", "x".repeat(4097)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAPI callback event identity is too large");
    }
}
