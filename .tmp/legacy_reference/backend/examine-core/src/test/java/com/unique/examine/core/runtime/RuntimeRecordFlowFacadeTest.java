package com.unique.examine.core.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordFlowFacadeTest {
    private static final Instant NOW = Instant.parse("2026-07-27T20:30:00Z");

    @Test
    void bindRequestKeepsAnImmutablePermissionSnapshot() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access",
                "module.purchase_order.view"));

        var request = new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, permissions, "purchase_order", 4L, 5L, NOW);
        permissions.add("module.purchase_order.update");

        assertThat(request.effectivePermissions())
                .containsExactlyInAnyOrder("system.runtime.access", "module.purchase_order.view");
        assertThatThrownBy(() -> request.effectivePermissions().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void transitionAcceptsOnlyTerminalStatuses() {
        for (var status : Set.of(
                RuntimeRecordFlowFacade.FlowStatus.APPROVED,
                RuntimeRecordFlowFacade.FlowStatus.REJECTED,
                RuntimeRecordFlowFacade.FlowStatus.WITHDRAWN,
                RuntimeRecordFlowFacade.FlowStatus.TERMINATED)) {
            assertThat(new RuntimeRecordFlowFacade.TransitionRequest(
                    1L, 2L, 3L, status, 4L, NOW).status()).isEqualTo(status);
        }

        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.TransitionRequest(
                1L, 2L, 3L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 4L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void rejectsInvalidBindingTransitionAndStateShapes() {
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.BindRequest(
                0L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IDs");
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase-order", 4L, 5L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module code");
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, null, "purchase_order", 4L, 5L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.TransitionRequest(
                1L, 2L, 3L, null, 4L, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.RecordFlowState(
                3L, RuntimeRecordFlowFacade.FlowStatus.PENDING, -1L, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void additionalBindingCopiesPermissionsAndCompactsTheEventKey() {
        var permissions = new HashSet<>(Set.of("module.purchase_order.view"));

        var request = new RuntimeRecordFlowFacade.AdditionalBindRequest(
                1L,
                2L,
                3L,
                permissions,
                "purchase_order",
                4L,
                5L,
                NOW,
                "  record:1:2:purchase_order:4:6:RECORD_ACTIVATED  ");
        permissions.add("unexpected");

        assertThat(request.effectivePermissions()).containsExactly("module.purchase_order.view");
        assertThat(request.eventKey())
                .isEqualTo("record:1:2:purchase_order:4:6:RECORD_ACTIVATED");
        assertThatThrownBy(() -> request.effectivePermissions().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.AdditionalBindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusMappingIsCanonicalAndOldBindingConstructorsRemainUnmapped() {
        var mapping = new RuntimeRecordFlowFacade.RecordStatusMapping(
                "approval_status", "101", "102", "103", "104");
        var mapped = new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW, mapping);
        var legacy = new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW);

        assertThat(mapped.recordStatusMapping()).isEqualTo(mapping);
        assertThat(legacy.recordStatusMapping()).isNull();
        assertThat(mapped.bindingSource()).isEqualTo(RuntimeRecordFlowFacade.BindingSource.MANUAL);
        assertThat(legacy.bindingSource()).isEqualTo(RuntimeRecordFlowFacade.BindingSource.MANUAL);
        assertThat(mapping.valueFor(RuntimeRecordFlowFacade.FlowStatus.APPROVED)).isEqualTo("101");
        assertThat(mapping.valueFor(RuntimeRecordFlowFacade.FlowStatus.TERMINATED)).isEqualTo("104");
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.RecordStatusMapping(
                "approval-status", "101", "102", "103", "104"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.RecordStatusMapping(
                "approval_status", "0", "102", "103", "104"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void automaticEventBindingSourceIsExplicitAndAvailableWithOrWithoutMapping() {
        var mapping = new RuntimeRecordFlowFacade.RecordStatusMapping(
                "approval_status", "101", "102", "103", "104");
        var primary = new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW,
                mapping, RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
        var additional = new RuntimeRecordFlowFacade.AdditionalBindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 6L, NOW, "event",
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);

        assertThat(primary.recordStatusMapping()).isEqualTo(mapping);
        assertThat(primary.bindingSource())
                .isEqualTo(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
        assertThat(additional.recordStatusMapping()).isNull();
        assertThat(additional.bindingSource())
                .isEqualTo(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
        assertThatThrownBy(() -> new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 5L, NOW, mapping, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bindingSource");
    }

    @Test
    void openApiBindingSourceIsExplicitWithoutChangingLegacyManualDefaults() {
        var openApi = new RuntimeRecordFlowFacade.BindRequest(
                1L,
                2L,
                3L,
                Set.of("module.purchase_order.view"),
                "purchase_order",
                4L,
                5L,
                NOW,
                RuntimeRecordFlowFacade.BindingSource.OPENAPI);
        var legacy = new RuntimeRecordFlowFacade.BindRequest(
                1L, 2L, 3L, Set.of(), "purchase_order", 4L, 6L, NOW);

        assertThat(openApi.bindingSource())
                .isEqualTo(RuntimeRecordFlowFacade.BindingSource.OPENAPI);
        assertThat(legacy.bindingSource())
                .isEqualTo(RuntimeRecordFlowFacade.BindingSource.MANUAL);
    }
}
