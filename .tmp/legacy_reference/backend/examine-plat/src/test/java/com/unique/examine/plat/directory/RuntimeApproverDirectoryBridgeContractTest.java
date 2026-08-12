package com.unique.examine.plat.directory;

import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeApproverDirectoryBridgeContractTest {
    @Test
    void roleAndDepartmentQueriesAreTenantScopedAndActiveOnly() {
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_ROLE_SQL).contains(
                "r.scope_type='SYSTEM'",
                "r.system_id=?",
                "r.tenant_id IS NULL OR r.tenant_id=?",
                "r.status='ACTIVE'",
                "FOR SHARE"
        );
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_ROLE_MEMBERS_SQL).contains(
                "mr.role_id=?",
                "mt.tenant_id=?",
                "mr.valid_from<=CURRENT_TIMESTAMP(3)",
                "mt.status='ACTIVE'",
                "ORDER BY m.id"
        );
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_DEPARTMENT_SQL).contains(
                "d.scope_type='SYSTEM'",
                "d.system_id=?",
                "d.status='ACTIVE'"
        );
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_DEPARTMENT_MEMBERS_SQL).contains(
                "md.department_id=?",
                "md.tenant_id IS NULL OR md.tenant_id=?",
                "mt.tenant_id=?",
                "ORDER BY m.id"
        );
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_DEPARTMENT_LEADER_SQL).contains(
                "un_plat_department_leader_assignment",
                "a.department_id=?",
                "a.status='ACTIVE'",
                "md.member_id=a.leader_member_id",
                "m.status='ACTIVE'",
                "mt.status='ACTIVE'",
                "FOR SHARE"
        );
        assertThat(RuntimeApproverDirectoryBridge.ACTIVE_REQUESTER_MANAGER_SQL).contains(
                "un_plat_member_manager_assignment",
                "a.member_id=?",
                "a.status='ACTIVE'",
                "m.id=a.manager_member_id",
                "mt.status='ACTIVE'",
                "FOR SHARE"
        );
    }

    @Test
    void resolutionCanonicalizesTheRuntimeSnapshot() {
        assertThat(RuntimeApproverDirectoryFacade.Resolution.active(
                List.of(30L, 10L, 30L, 20L)
        ).memberIds()).containsExactly(10L, 20L, 30L);
        assertThat(RuntimeApproverDirectoryFacade.Resolution.missing().sourceActive())
                .isFalse();
    }

    @Test
    void bridgeRequiresTheCallersTransaction() throws Exception {
        assertThat(RuntimeApproverDirectoryBridge.class.getMethod(
                "resolveRoleMembers",
                long.class,
                long.class,
                long.class
        ).getAnnotation(Transactional.class).propagation().name()).isEqualTo("MANDATORY");
        assertThat(RuntimeApproverDirectoryBridge.class.getMethod(
                "resolveDepartmentMembers",
                long.class,
                long.class,
                long.class
        ).getAnnotation(Transactional.class).propagation().name()).isEqualTo("MANDATORY");
        assertThat(RuntimeApproverDirectoryBridge.class.getMethod(
                "resolveDepartmentLeader",
                long.class,
                long.class,
                long.class
        ).getAnnotation(Transactional.class).propagation().name()).isEqualTo("MANDATORY");
        assertThat(RuntimeApproverDirectoryBridge.class.getMethod(
                "resolveRequesterManager",
                long.class,
                long.class,
                long.class
        ).getAnnotation(Transactional.class).propagation().name()).isEqualTo("MANDATORY");
    }
}
