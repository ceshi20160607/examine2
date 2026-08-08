package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleRuntimeFieldPermissionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void collectsEveryEnabledPermissionWithoutSingleValueOverwrite() throws Exception {
        var snapshot = objectMapper.readTree("""
                {"permissions":[
                  {"resource_type":"FIELD","resource_id":"11","permission_code":"module.orders.field.amount.read","desired_status":"ENABLED"},
                  {"resource_type":"FIELD","resource_id":"11","permission_code":"module.orders.field.amount.write","desired_status":"ENABLED"},
                  {"resource_type":"FIELD","resource_id":"11","permission_code":"module.orders.field.amount.sensitive.read","desired_status":"ENABLED"},
                  {"resource_type":"FIELD","resource_id":"11","permission_code":"module.orders.field.amount.sensitive.query","desired_status":"DISABLED"}
                ]}
                """);

        assertThat(ModuleRuntimeService.permissionsByResource(snapshot, "FIELD").get("11"))
                .containsExactlyInAnyOrder(
                        "module.orders.field.amount.read",
                        "module.orders.field.amount.write",
                        "module.orders.field.amount.sensitive.read");
    }

    @Test
    void onlyExactReadDeclarationCanHideAField() {
        var read = "module.orders.field.amount.read";
        assertThat(ModuleRuntimeService.fieldReadable(Set.of(
                "module.orders.field.amount.write",
                "module.orders.field.amount.sensitive.read"), read, Set.of())).isTrue();
        assertThat(ModuleRuntimeService.fieldReadable(Set.of(read), read, Set.of())).isFalse();
        assertThat(ModuleRuntimeService.fieldReadable(Set.of(read), read, Set.of(read))).isTrue();
    }
}
