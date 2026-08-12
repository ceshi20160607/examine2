package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordPolicyCatalogFacadeTest {

    @Test
    void requestAndResultKeepImmutablePermissionAndFieldSnapshots() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access", "module.work_order.view"));
        var request = new AiRecordPolicyCatalogFacade.Request(
                11L, 13L, 17L, permissions, "work_order");
        permissions.add("module.work_order.update");

        var fields = new HashSet<>(Set.of("name", "owner_id"));
        var result = new AiRecordPolicyCatalogFacade.Result(
                "work_order", "41", 7L, fields);
        fields.add("secret_note");

        assertThat(request.effectivePermissions()).containsExactlyInAnyOrder(
                "system.runtime.access", "module.work_order.view");
        assertThat(result.readableFieldCodes()).containsExactlyInAnyOrder(
                "name", "owner_id");
        assertThatThrownBy(() -> result.readableFieldCodes().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidContextAndCatalogShapes() {
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Request(
                0L, 13L, 17L, Set.of(), "work_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IDs");
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Request(
                11L, 13L, 17L, null, "work_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Request(
                11L, 13L, 17L, Set.of(), "work-order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module code");
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Result(
                "work_order", "0", 1L, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema version ID");
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Result(
                "work_order", "41", -1L, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorization epoch");
        assertThatThrownBy(() -> new AiRecordPolicyCatalogFacade.Result(
                "work_order", "41", 1L, Set.of("bad-field")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable field code");
    }
}
