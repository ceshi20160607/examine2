package com.unique.examine.module.report.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportDomainTest {
    private static final Instant NOW =
            Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void rootNormalizesEditableTextAndKeepsCodeImmutable() {
        var root = ReportDefinition.create(
                1, 10, 20, "salesReport", "  Sales report  ", "   ",
                new ReportDraft(100, List.of("title")), NOW);

        var revised = root.reviseDraft(
                " Revised ", " Description ",
                new ReportDraft(100, List.of("amount", "title")), NOW);

        assertThat(root.name()).isEqualTo("Sales report");
        assertThat(root.description()).isNull();
        assertThat(revised.code()).isEqualTo("salesReport");
        assertThat(revised.name()).isEqualTo("Revised");
        assertThat(revised.description()).isEqualTo("Description");
        assertThat(revised.draftVersion()).isEqualTo(2);
        assertThat(revised.version()).isEqualTo(2);
    }

    @Test
    void draftPreservesOrderForCheckAndBoundsTheUntrustedShape() {
        var selected = new ArrayList<>(List.of("amount", "title", "amount"));
        var draft = new ReportDraft(100, selected);
        selected.clear();

        assertThat(draft.outputFieldCodes())
                .containsExactly("amount", "title", "amount");
        assertThat(draft.canonicalForm())
                .isEqualTo("source:3:100|fields:3|6:amount|5:title|6:amount|");
        assertThatThrownBy(() -> new ReportDraft(100,
                java.util.stream.IntStream.range(0, 101)
                        .mapToObj(index -> "field" + index).toList()))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_DRAFT_INVALID"));
    }

    @Test
    void publishedPinsAreOrderedImmutableAndRejectDuplicateIdentity() {
        var fields = new ArrayList<>(List.of(
                field(1, "amount"), field(2, "title")));
        var pin = new ReportSourcePin(
                100, "orders", "Orders", 501, 2, 300,
                "orders_module", "schema-2", fields);
        fields.clear();

        assertThat(pin.fields()).extracting(ReportFieldPin::code)
                .containsExactly("amount", "title");
        assertThatThrownBy(() -> new ReportSourcePin(
                100, "orders", "Orders", 501, 2, 300,
                "orders_module", "schema-2",
                List.of(field(1, "amount"), field(1, "title"))))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_SOURCE_PIN_INVALID"));
    }

    private static ReportFieldPin field(long id, String code) {
        return new ReportFieldPin(
                id, code, code, "TEXT", "TEXT");
    }
}
