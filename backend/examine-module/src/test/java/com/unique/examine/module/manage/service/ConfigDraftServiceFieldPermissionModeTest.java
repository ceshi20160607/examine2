package com.unique.examine.module.manage.service;

import com.unique.examine.module.manage.api.ConfigTypes.DesiredStatus;
import com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDraftServiceFieldPermissionModeTest {
    @Test
    void createDefaultsOmittedModeToInherit() {
        assertThat(ConfigDraftService.createPermissionMode(null)).isEqualTo(FieldPermissionMode.INHERIT);
        assertThat(ConfigDraftService.createPermissionMode(FieldPermissionMode.STAGED))
                .isEqualTo(FieldPermissionMode.STAGED);
    }

    @Test
    void updatePreservesCurrentModeWhenOmitted() {
        assertThat(ConfigDraftService.updatePermissionMode(null, FieldPermissionMode.ENFORCED))
                .isEqualTo(FieldPermissionMode.ENFORCED);
        assertThat(ConfigDraftService.updatePermissionMode(
                FieldPermissionMode.INHERIT, FieldPermissionMode.ENFORCED))
                .isEqualTo(FieldPermissionMode.INHERIT);
    }

    @Test
    void mapsModesToExistingPermissionStatusesAndBack() {
        assertThat(ConfigDraftService.permissionStatus(FieldPermissionMode.INHERIT))
                .isEqualTo(DesiredStatus.ARCHIVED);
        assertThat(ConfigDraftService.permissionStatus(FieldPermissionMode.STAGED))
                .isEqualTo(DesiredStatus.DISABLED);
        assertThat(ConfigDraftService.permissionStatus(FieldPermissionMode.ENFORCED))
                .isEqualTo(DesiredStatus.ENABLED);
        assertThat(ConfigDraftService.permissionMode("ARCHIVED")).isEqualTo(FieldPermissionMode.INHERIT);
        assertThat(ConfigDraftService.permissionMode("DISABLED")).isEqualTo(FieldPermissionMode.STAGED);
        assertThat(ConfigDraftService.permissionMode("ENABLED")).isEqualTo(FieldPermissionMode.ENFORCED);
        assertThat(ConfigDraftService.permissionMode(null)).isEqualTo(FieldPermissionMode.INHERIT);
    }

    @Test
    void rejectsWriteDeclarationsForOwnerComputedFields() {
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.TEXT, false)).isTrue();
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.TEXT, true)).isFalse();
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.REFERENCE, false)).isFalse();
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.FORMULA, false)).isFalse();
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.AI_FILL, false)).isFalse();
        assertThat(ConfigDraftService.supportsWritePermission(FieldType.AUTO_NUMBER, false)).isFalse();
    }
}
