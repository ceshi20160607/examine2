package com.unique.examine.module.runtime.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoHashCodecTest {
    @Test
    void encodesThePublishedGeohashReferenceVector() {
        assertThat(GeoHashCodec.encode(new BigDecimal("42.6"), new BigDecimal("-5.6"), 5))
                .isEqualTo("ezs42");
        assertThat(GeoHashCodec.encode(new BigDecimal("39.9"), new BigDecimal("116.4"), 12))
                .hasSize(12).startsWith("wx4");
    }

    @Test
    void rejectsCoordinatesOutsideWgs84() {
        assertThatThrownBy(() -> GeoHashCodec.encode(new BigDecimal("91"), BigDecimal.ZERO, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
