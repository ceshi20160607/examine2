package com.unique.examine.module.manage.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPermissionCodesTest {
    @Test
    void buildsOnlyCanonicalGenericCodes() {
        assertThat(FieldPermissionCodes.read("orders", "amount"))
                .isEqualTo("module.orders.field.amount.read");
        assertThat(FieldPermissionCodes.write("orders", "amount"))
                .isEqualTo("module.orders.field.amount.write");
        assertThat(FieldPermissionCodes.isGeneric("module.orders.field.amount.read")).isTrue();
        assertThat(FieldPermissionCodes.isGeneric("module.orders.field.amount.write")).isTrue();
    }

    @Test
    void excludesSensitiveAndNonCanonicalCodes() {
        assertThat(FieldPermissionCodes.isGeneric("module.orders.field.amount.sensitive.read")).isFalse();
        assertThat(FieldPermissionCodes.isGeneric("module.orders.field.amount.export")).isFalse();
        assertThat(FieldPermissionCodes.isGeneric("module.orders.view")).isFalse();
    }
}
