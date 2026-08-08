package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordFlowEventValueSnapshotTest {

    @Test
    void encodesPersistedValuesAsCompactCanonicalJsonAndOmitsSensitiveTypes() {
        var values = List.of(
                value("amount", "NUMBER", new BigDecimal("100.00")),
                value("urgent", "SWITCH", true),
                value("remark", "TEXT", "needs \"review\""),
                value("tags", "MULTI_SELECT", List.of("a", "b")),
                value("identity_no", "IDENTITY", "110101199001010000"),
                value("api_secret", "SECRET", "hidden"));

        var snapshot = RecordFlowEventValueSnapshot.from(new ObjectMapper(), values);

        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
                "amount", "100.00",
                "urgent", "true",
                "remark", "\"needs \\\"review\\\"\"",
                "tags", "[\"a\",\"b\"]"));
        assertThat(snapshot).doesNotContainKeys("identity_no", "api_secret");
        assertThatThrownBy(() -> snapshot.put("later", "1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsOversizedValueAndFieldCount() {
        assertThatThrownBy(() -> RecordFlowEventValueSnapshot.from(
                new ObjectMapper(),
                List.of(value("remark", "TEXT", "界".repeat(6000)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 KiB");

        var values = new ArrayList<RecordFlowEventValueSnapshot.Value>();
        for (var index = 0; index < 257; index++) {
            values.add(value("field_" + index, "TEXT", index));
        }
        assertThatThrownBy(() -> RecordFlowEventValueSnapshot.from(new ObjectMapper(), values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 fields");
    }

    private static RecordFlowEventValueSnapshot.Value value(
            String fieldCode,
            String fieldType,
            Object value
    ) {
        return new RecordFlowEventValueSnapshot.Value(fieldCode, fieldType, value);
    }
}
