package com.unique.examine.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordFlowTriggerFacadeTest {

    @Test
    void exposesTheFrozenRuntimeRecordEvents() {
        assertThat(RuntimeRecordFlowTriggerFacade.TriggerEvent.values())
                .containsExactly(
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_CREATED,
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_UPDATED,
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_DELETED,
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_STATUS_CHANGED,
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.IMPORT_COMPLETED);
    }

    @Test
    void requestCompactsKeysAndCopiesPermissions() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access",
                "module.purchase_order.view"));
        var recordValues = new LinkedHashMap<>(Map.of(
                "amount", "100.00",
                "urgent", "true"));

        var request = new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                1L,
                2L,
                3L,
                permissions,
                "purchase_order",
                4L,
                5L,
                "  PO-8  ",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                "  record:1:2:purchase_order:4:5:RECORD_ACTIVATED  ",
                recordValues);
        permissions.add("module.purchase_order.update");
        recordValues.put("remark", "\"later\"");

        assertThat(request.businessKey()).isEqualTo("PO-8");
        assertThat(request.eventKey())
                .isEqualTo("record:1:2:purchase_order:4:5:RECORD_ACTIVATED");
        assertThat(request.effectivePermissions())
                .containsExactlyInAnyOrder("system.runtime.access", "module.purchase_order.view");
        assertThat(request.recordValuesJson())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "amount", "100.00",
                        "urgent", "true"));
        assertThatThrownBy(() -> request.effectivePermissions().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.recordValuesJson().put("remark", "\"later\""))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultCopiesInstancesAndSupportsExplicitNoMatch() {
        var source = new ArrayList<RuntimeRecordFlowTriggerFacade.TriggeredInstance>();
        source.add(new RuntimeRecordFlowTriggerFacade.TriggeredInstance(11L, 2, 22L));

        var result = new RuntimeRecordFlowTriggerFacade.TriggerResult(source);
        source.clear();

        assertThat(result.instances()).containsExactly(
                new RuntimeRecordFlowTriggerFacade.TriggeredInstance(11L, 2, 22L));
        assertThat(new RuntimeRecordFlowTriggerFacade.TriggerResult(java.util.List.of()).instances())
                .isEmpty();
        assertThatThrownBy(() -> result.instances().add(
                new RuntimeRecordFlowTriggerFacade.TriggeredInstance(12L, 1, 23L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidIdsVersionModuleAndCompactText() {
        assertInvalid(() -> request(0L, 5L, "purchase_order", "PO-8", "event"));
        assertInvalid(() -> request(4L, -1L, "purchase_order", "PO-8", "event"));
        assertInvalid(() -> request(4L, 5L, "purchase-order", "PO-8", "event"));
        assertInvalid(() -> request(4L, 5L, "purchase_order", " ", "event"));
        assertInvalid(() -> request(
                4L,
                5L,
                "purchase_order",
                "x".repeat(161),
                "event"));
        assertInvalid(() -> request(
                4L,
                5L,
                "purchase_order",
                "PO-8",
                "界".repeat(201)));
        assertInvalid(() -> request(Map.of("bad-code", "1")));
        assertInvalid(() -> request(Map.of("amount", "{ \"value\": 1 }")));
        assertInvalid(() -> request(Map.of("amount", "{")));
        assertInvalid(() -> request(Map.of("remark", "\"" + "界".repeat(8192) + "\"")));
        var tooMany = new LinkedHashMap<String, String>();
        for (var index = 0; index < 257; index++) {
            tooMany.put("field_" + index, "null");
        }
        assertInvalid(() -> request(tooMany));
        assertThatThrownBy(() -> new RuntimeRecordFlowTriggerFacade.TriggeredInstance(1L, 0, 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeRecordFlowTriggerFacade.TriggerResult(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RuntimeRecordFlowTriggerFacade.TriggerRequest request(
            long recordId,
            long recordVersion,
            String moduleCode,
            String businessKey,
            String eventKey
    ) {
        return new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                1L,
                2L,
                3L,
                Set.of(),
                moduleCode,
                recordId,
                recordVersion,
                businessKey,
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                eventKey);
    }

    private static RuntimeRecordFlowTriggerFacade.TriggerRequest request(Map<String, String> values) {
        return new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                1L,
                2L,
                3L,
                Set.of(),
                "purchase_order",
                4L,
                5L,
                "PO-8",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                "event",
                values);
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }
}
