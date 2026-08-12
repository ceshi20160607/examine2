package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordQueryFacadeTest {

    @Test
    void requestAndResultKeepImmutableSnapshots() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access", "module.work_order.view"));
        var fields = new ArrayList<>(List.of("name"));
        var request = new AiRecordQueryFacade.Request(
                11L, 13L, 17L, permissions, "work_order", "{}", fields, 10);
        permissions.add("module.work_order.update");
        fields.add("secret_note");

        assertThat(request.effectivePermissions()).containsExactlyInAnyOrder(
                "system.runtime.access", "module.work_order.view");
        assertThat(request.outboundFieldCodes()).containsExactly("name");
        assertThatThrownBy(() -> request.outboundFieldCodes().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);

        var values = new ArrayList<>(List.of(
                new AiRecordQueryFacade.DisplayValue("name", "Alpha")));
        var rows = new ArrayList<>(List.of(new AiRecordQueryFacade.Record(
                "23", "WO-23", 4L, "ACTIVE", "Work order", values)));
        var result = new AiRecordQueryFacade.Result(1L, rows);
        values.add(new AiRecordQueryFacade.DisplayValue("other", "Hidden"));
        rows.clear();

        assertThat(result.records()).singleElement().satisfies(record ->
                assertThat(record.values()).containsExactly(
                        new AiRecordQueryFacade.DisplayValue("name", "Alpha")));
    }

    @Test
    void rejectsInvalidContextsBoundsAndShapes() {
        assertThatThrownBy(() -> request(0L, "work_order", List.of("name"), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IDs");
        assertThatThrownBy(() -> request(11L, "work-order", List.of("name"), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module code");
        assertThatThrownBy(() -> request(11L, "work_order", List.of("name", "name"), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field codes");
        assertThatThrownBy(() -> request(11L, "work_order", List.of("name"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..50");
        assertThatThrownBy(() -> request(11L, "work_order", List.of("name"), 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..50");
        assertThatThrownBy(() -> new AiRecordQueryFacade.Request(
                11L, 13L, 17L, null, "work_order", "{}", List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> new AiRecordQueryFacade.Request(
                11L, 13L, 17L, Set.of(), "work_order", " ", List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> new AiRecordQueryFacade.Result(-1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
        assertThatThrownBy(() -> new AiRecordQueryFacade.Record(
                "0", "WO-0", 0L, "ACTIVE", "Invalid", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("record ID");
    }

    @Test
    void publicContractContainsNoRawFieldValueCarrier() {
        assertThat(AiRecordQueryFacade.DisplayValue.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("fieldCode", "displayValue");
        assertThat(AiRecordQueryFacade.Result.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("total", "records");
    }

    private static AiRecordQueryFacade.Request request(
            long systemId,
            String moduleCode,
            List<String> fields,
            int maxRows
    ) {
        return new AiRecordQueryFacade.Request(
                systemId, 13L, 17L, Set.of(), moduleCode, "{}", fields, maxRows);
    }
}
