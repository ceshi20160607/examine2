package com.unique.examine.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordAccessFacadeTest {

    @Test
    void requestKeepsAnImmutablePermissionSnapshot() {
        var permissions = new HashSet<>(Set.of("system.runtime.access", "module.work_order.view"));

        var request = new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                1L, 2L, 3L, permissions, "work_order", 4L);
        permissions.add("module.work_order.update");

        assertThat(request.effectivePermissions())
                .containsExactlyInAnyOrder("system.runtime.access", "module.work_order.view");
        assertThatThrownBy(() -> request.effectivePermissions().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidContextAndOutputShapes() {
        assertThatThrownBy(() -> new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                0L, 2L, 3L, Set.of(), "work_order", 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IDs");
        assertThatThrownBy(() -> new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                1L, 2L, 3L, Set.of(), "work-order", 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module code");
        assertThatThrownBy(() -> new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                1L, 2L, 3L, null, "work_order", 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("0", 1L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("record ID");
        assertThatThrownBy(() -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("4", -1L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }
}
