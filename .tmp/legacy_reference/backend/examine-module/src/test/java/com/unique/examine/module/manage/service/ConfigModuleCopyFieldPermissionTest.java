package com.unique.examine.module.manage.service;

import com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigModuleCopyFieldPermissionTest {
    @Test
    void demotesEveryDeclaredPermissionAndKeepsInherit() {
        assertThat(ConfigModuleCopyService.copiedPermissionMode(FieldPermissionMode.INHERIT))
                .isEqualTo(FieldPermissionMode.INHERIT);
        assertThat(ConfigModuleCopyService.copiedPermissionMode(FieldPermissionMode.STAGED))
                .isEqualTo(FieldPermissionMode.STAGED);
        assertThat(ConfigModuleCopyService.copiedPermissionMode(FieldPermissionMode.ENFORCED))
                .isEqualTo(FieldPermissionMode.STAGED);
        assertThat(ConfigModuleCopyService.copiedPermissionMode(null))
                .isEqualTo(FieldPermissionMode.INHERIT);
    }
}
