package com.unique.examine.module.runtime.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivedMaterializationServiceTest {
    @Test
    void enforcesLookupCardinalityAtTheExactContractBoundary() {
        assertThatCode(() -> DerivedMaterializationService.requireLookupCardinality(100))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DerivedMaterializationService.requireLookupCardinality(101))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LOOKUP result exceeds cardinality 100");
    }
}
