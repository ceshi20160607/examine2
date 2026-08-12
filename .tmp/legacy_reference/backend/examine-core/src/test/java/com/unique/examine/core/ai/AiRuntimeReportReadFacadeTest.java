package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRuntimeReportReadFacadeTest {
    private static final String MODULE = "work_order";

    @Test
    void requestKeepsServerAuthoritySnapshotsAndEnforcesPageBounds() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access", "module.work_order.view"));
        var modules = new HashSet<>(Set.of(MODULE));
        var fields = new HashSet<>(Set.of("status", "amount"));
        var outbound = new HashMap<String, Set<String>>();
        outbound.put(MODULE, fields);
        var request = new AiRuntimeReportReadFacade.Request(
                11L, 13L, 17L, permissions, modules, outbound, 10,
                MODULE, "operations", 2, 10);

        permissions.add("module.work_order.update");
        modules.add("other_module");
        fields.add("secret_note");
        outbound.put("other_module", Set.of("secret_note"));

        assertThat(request.effectivePermissions()).containsExactlyInAnyOrder(
                "system.runtime.access", "module.work_order.view");
        assertThat(request.allowedModuleCodes()).containsExactly(MODULE);
        assertThat(request.outboundFields()).containsOnlyKeys(MODULE);
        assertThat(request.outboundFields().get(MODULE))
                .containsExactlyInAnyOrder("status", "amount");
        assertThatThrownBy(() -> request.outboundFields().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> request(0, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> request(10_001, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> request(1, 11, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows");
        assertThatThrownBy(() -> request(1, 51, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows");
    }

    @Test
    void resultPreservesNullableDisplayStringsAndExactFieldOrder() {
        var fields = new ArrayList<>(List.of(
                new AiRuntimeReportReadFacade.Field(
                        "status", "Status", "STATUS"),
                new AiRuntimeReportReadFacade.Field(
                        "amount", "Amount", "NUMBER")));
        var values = new ArrayList<>(List.of(
                new AiRuntimeReportReadFacade.Value("status", null),
                new AiRuntimeReportReadFacade.Value(
                        "amount", "12345678901234567890.123400")));
        var rows = new ArrayList<>(List.of(
                new AiRuntimeReportReadFacade.Row(values)));

        var result = new AiRuntimeReportReadFacade.Result(
                "operations", "Operations", 3,
                "active_orders", 7, MODULE,
                2, 10, 25L, 1, true,
                "/systems/11/reports/operations", fields, rows);
        fields.clear();
        values.clear();
        rows.clear();

        assertThat(result.returnedRows()).isEqualTo(1);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.fields()).extracting(
                        AiRuntimeReportReadFacade.Field::fieldCode)
                .containsExactly("status", "amount");
        assertThat(result.rows().getFirst().values())
                .extracting(AiRuntimeReportReadFacade.Value::displayValue)
                .containsExactly(null, "12345678901234567890.123400");
        assertThatThrownBy(() -> result.rows().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultRejectsMismatchedCountsOrderRouteAndHasMore() {
        var fields = List.of(new AiRuntimeReportReadFacade.Field(
                "status", "Status", "STATUS"));
        var row = new AiRuntimeReportReadFacade.Row(List.of(
                new AiRuntimeReportReadFacade.Value("amount", "1")));

        assertThatThrownBy(() -> new AiRuntimeReportReadFacade.Result(
                "operations", "Operations", 1, "active_orders", 1,
                MODULE, 1, 10, 1L, 1, false,
                "/systems/11/reports/operations", fields, List.of(row)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order");
        assertThatThrownBy(() -> new AiRuntimeReportReadFacade.Result(
                "operations", "Operations", 1, "active_orders", 1,
                MODULE, 1, 10, 20L, 0, false,
                "/systems/11/reports/operations", fields, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hasMore");
        assertThatThrownBy(() -> new AiRuntimeReportReadFacade.Result(
                "operations", "Operations", 1, "active_orders", 1,
                MODULE, 1, 10, 0L, 0, false,
                "/systems/12/reports/other", fields, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("route");
    }

    @Test
    void publicResultContainsNoRawOrRecordEnvelopeCarrier() {
        assertThat(AiRuntimeReportReadFacade.Value.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("fieldCode", "displayValue")
                .doesNotContain("value");
        assertThat(AiRuntimeReportReadFacade.Row.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("values")
                .doesNotContain("recordId", "recordNo", "title", "status",
                        "version");
        assertThat(AiRuntimeReportReadFacade.Result.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("queryHash", "reportId", "reportVersionId",
                        "dataSourceId", "dataSourceVersionId",
                        "schemaVersionId");
    }

    private static AiRuntimeReportReadFacade.Request request(
            int page,
            int size,
            int maxRows
    ) {
        return new AiRuntimeReportReadFacade.Request(
                11L, 13L, 17L, Set.of(), Set.of(MODULE),
                Map.of(MODULE, Set.of("status")), maxRows,
                MODULE, "operations", page, size);
    }
}
