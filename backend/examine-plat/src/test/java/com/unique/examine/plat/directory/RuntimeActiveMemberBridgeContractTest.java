package com.unique.examine.plat.directory;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeActiveMemberBridgeContractTest {
    @Test
    void locksOnlyActiveCurrentTenantMembershipBeforeResolvingPrimaryDepartment() {
        assertThat(RuntimeActiveMemberBridge.ACTIVE_MEMBER_LOCK_SQL)
                .contains("mt.system_id=?")
                .contains("mt.tenant_id=?")
                .contains("mt.member_id=?")
                .contains("mt.status='ACTIVE'")
                .contains("mt.deleted_at IS NULL")
                .contains("mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3)")
                .contains("m.status='ACTIVE'")
                .contains("m.deleted_at IS NULL")
                .containsIgnoringCase("FOR UPDATE");
        assertThat(RuntimeActiveMemberBridge.PRIMARY_DEPARTMENT_LOCK_SQL)
                .contains("md.is_primary=1")
                .contains("md.deleted_at IS NULL")
                .contains("md.tenant_id IS NULL OR md.tenant_id=?")
                .containsIgnoringCase("FOR UPDATE");
    }

    @Test
    void requiresTheCallersExistingTransaction() throws Exception {
        var method = RuntimeActiveMemberBridge.class.getMethod(
                "lockActiveMember", long.class, long.class, long.class);
        assertThat(method.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }
}
