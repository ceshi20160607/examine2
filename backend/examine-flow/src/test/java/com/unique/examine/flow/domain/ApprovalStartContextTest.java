package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalStartContextTest {
    private static final Instant PUBLISHED =
            Instant.parse("2026-07-31T03:00:00Z");
    private static final Instant STARTED = PUBLISHED.plusSeconds(60);

    @Test
    void freezesCanonicalAuthorizedValuesBeforeExecution() {
        var mutable = new LinkedHashMap<String, String>();
        mutable.put("owner_id", " 11 ");
        mutable.put("amount", "{ \"currency\" : \"CNY\", \"value\" : 100 }");
        var context = new ApprovalStartContext(
                9L, "purchase_order", 901L, mutable);
        mutable.clear();

        assertThat(context.valuesJson())
                .containsExactly(
                        Map.entry("amount", "{\"currency\":\"CNY\",\"value\":100}"),
                        Map.entry("owner_id", "11")
                );
        assertThatThrownBy(() -> context.valuesJson().put("late", "1"))
                .isInstanceOf(UnsupportedOperationException.class);

        var instance = ApprovalInstance.start(
                201L,
                new ApprovalDefinitionVersion(
                        101L, 1, "Context", List.of(11L), 1, PUBLISHED),
                "context-1",
                9L,
                STARTED,
                new ApprovalInstance.RecordBinding("purchase_order", 901L),
                context
        );
        var approved = instance.approve(
                11L, "approved", STARTED.plusSeconds(1));

        assertThat(approved.startContext()).isSameAs(context);
        assertThat(approved.startContext().valuesJson())
                .isEqualTo(context.valuesJson());
    }

    @Test
    void rejectsMismatchedIdentityInvalidJsonAndOversizedMaps() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Context", List.of(11L), 1, PUBLISHED);
        var mismatched = new ApprovalStartContext(
                9L, "purchase_order", 902L, Map.of());

        assertThatThrownBy(() -> ApprovalInstance.start(
                202L, definition, "context-mismatch", 9L, STARTED,
                new ApprovalInstance.RecordBinding("purchase_order", 901L),
                mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mirror the record binding");
        assertThatThrownBy(() -> new ApprovalStartContext(
                9L, null, null, Map.of("amount", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON");

        var oversized = new LinkedHashMap<String, String>();
        for (var index = 0; index <= ApprovalStartContext.MAX_VALUES; index++) {
            oversized.put("field_" + index, Integer.toString(index));
        }
        assertThatThrownBy(() -> new ApprovalStartContext(
                9L, null, null, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many");
    }
}
