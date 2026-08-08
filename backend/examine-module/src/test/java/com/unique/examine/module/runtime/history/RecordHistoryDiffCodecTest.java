package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordHistoryDiffCodecTest {
    private final RecordHistoryDiffCodec codec = new RecordHistoryDiffCodec(new ObjectMapper());

    @Test
    void storesOnlyChangedMetadataAndFieldValues() {
        var before = record(
                "Old title",
                "ACTIVE",
                field("stable", "TEXT", "same", "same"),
                field("changed", "TEXT", "old", "old"));
        var after = record(
                "New title",
                "ACTIVE",
                field("stable", "TEXT", "same", "same"),
                field("changed", "TEXT", "new", "new"));

        var diff = codec.changed(before, after);

        assertThat(diff).extracting(RecordHistoryDiff::fieldCode)
                .containsExactly("$title", "changed");
        assertThat(diff.getFirst().beforeValue().asText()).isEqualTo("Old title");
        assertThat(diff.getFirst().afterValue().asText()).isEqualTo("New title");
    }

    @Test
    void asymmetricLifecycleSnapshotsDoNotInventFieldChanges() {
        var before = Map.of("status", "DRAFT", "version", 0);
        var after = record(
                "Work order",
                "ACTIVE",
                field("description", "TEXT", "unchanged", "unchanged"));

        var diff = codec.changed(before, after);

        assertThat(diff).singleElement().satisfies(item -> {
            assertThat(item.fieldCode()).isEqualTo("$status");
            assertThat(item.beforeValue().asText()).isEqualTo("DRAFT");
            assertThat(item.afterValue().asText()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void sensitiveValuesAreAlwaysReducedToMasksAndCanBeForcedAsChanged() throws Exception {
        var before = record(
                "Work order",
                "ACTIVE",
                field("identity", "IDENTITY", "plaintext-identity-before", "********0001"),
                field("secret", "SECRET", "plaintext-secret-before", "********"));
        var after = record(
                "Work order",
                "ACTIVE",
                field("identity", "IDENTITY", "plaintext-identity-after", "********0002"),
                field("secret", "SECRET", "plaintext-secret-after", "********"));

        var diff = codec.changed(before, after, Set.of("secret"));
        var persisted = new ObjectMapper().writeValueAsString(diff);

        assertThat(diff).extracting(RecordHistoryDiff::fieldCode)
                .containsExactly("identity", "secret");
        assertThat(diff).allMatch(RecordHistoryDiff::masked);
        assertThat(persisted)
                .doesNotContain("plaintext-identity-before")
                .doesNotContain("plaintext-identity-after")
                .doesNotContain("plaintext-secret-before")
                .doesNotContain("plaintext-secret-after");
    }

    private static RecordRuntimeViews.RecordDetail record(
            String title,
            String status,
            RecordRuntimeViews.FieldValue... fields
    ) {
        return new RecordRuntimeViews.RecordDetail(
                "3",
                "WO-3",
                1,
                status,
                title,
                "100",
                List.of(fields),
                List.of());
    }

    private static RecordRuntimeViews.FieldValue field(
            String code,
            String type,
            Object value,
            String display
    ) {
        return new RecordRuntimeViews.FieldValue(code, code, type, value, display);
    }
}
