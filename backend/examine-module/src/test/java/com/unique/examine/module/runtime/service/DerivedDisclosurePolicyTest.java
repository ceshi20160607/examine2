package com.unique.examine.module.runtime.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedDisclosurePolicyTest {

    @Test
    void omitsHiddenLookupItemsAndNeverReturnsAPartialSummary() {
        var dependencies = Map.of(
                "visible", Set.of(11L),
                "hidden", Set.of(12L),
                "empty", Set.<Long>of());

        assertThat(RecordRuntimeService.filterDerivedDisclosure(
                "LOOKUP", List.of("visible", "hidden", "empty"), dependencies, Set.of(11L)))
                .containsExactly("visible", "empty");
        assertThat(RecordRuntimeService.filterDerivedDisclosure(
                "SUMMARY", List.of("summary"), Map.of("summary", Set.of(11L, 12L)), Set.of(11L)))
                .isEmpty();
        assertThat(RecordRuntimeService.filterDerivedDisclosure(
                "SUMMARY", List.of("summary"), Map.of("summary", Set.of(11L, 12L)), Set.of(11L, 12L)))
                .containsExactly("summary");
    }
}
