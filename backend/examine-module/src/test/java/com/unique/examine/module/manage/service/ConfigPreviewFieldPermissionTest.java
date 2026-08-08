package com.unique.examine.module.manage.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPreviewFieldPermissionTest {
    @Test
    void writeAndSensitivePermissionsNeverReplaceExactReadPreview() {
        var read = "module.orders.field.amount.read";
        var unrelated = Set.of(
                "module.orders.field.amount.write",
                "module.orders.field.amount.sensitive.read");

        assertThat(ConfigPreviewService.allowedByExactPermission(unrelated, read, Set.of(), false)).isTrue();
        assertThat(ConfigPreviewService.allowedByExactPermission(Set.of(read), read, Set.of(), false)).isFalse();
        assertThat(ConfigPreviewService.allowedByExactPermission(Set.of(read), read, Set.of(read), false)).isTrue();
        assertThat(ConfigPreviewService.allowedByExactPermission(Set.of(read), read, Set.of(), true)).isTrue();
    }
}
