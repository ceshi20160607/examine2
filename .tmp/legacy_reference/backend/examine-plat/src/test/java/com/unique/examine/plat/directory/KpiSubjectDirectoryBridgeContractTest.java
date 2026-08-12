package com.unique.examine.plat.directory;

import com.unique.examine.core.api.KpiSubjectDirectoryFacade;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KpiSubjectDirectoryBridgeContractTest {
    @Test
    void subjectsAreActiveTenantScopedAndNamed() {
        assertThat(KpiSubjectDirectoryBridge.ACTIVE_MEMBER_SQL).contains(
                "m.display_name", "m.system_id=?", "m.id=?",
                "mt.tenant_id=?", "m.status='ACTIVE'",
                "mt.status='ACTIVE'", "mt.expires_at>CURRENT_TIMESTAMP(3)",
                "FOR SHARE");
        assertThat(KpiSubjectDirectoryBridge.ACTIVE_DEPARTMENT_SQL).contains(
                "d.name", "d.scope_type='SYSTEM'", "d.scope_key=?",
                "d.system_id=?", "d.tenant_id IS NULL OR d.tenant_id=?",
                "d.status='ACTIVE'", "d.deleted_at IS NULL", "FOR SHARE");
        assertThat(KpiSubjectDirectoryBridge.ACTIVE_ROLE_SQL).contains(
                "r.name", "r.scope_type='SYSTEM'", "r.scope_key=?",
                "r.system_id=?", "r.tenant_id IS NULL OR r.tenant_id=?",
                "r.status='ACTIVE'", "r.deleted_at IS NULL", "FOR SHARE");
    }

    @Test
    void roleSnapshotContainsOnlyCurrentActiveTenantMembersInIdOrder() {
        assertThat(KpiSubjectDirectoryBridge.ACTIVE_ROLE_MEMBERS_SQL).contains(
                "SELECT DISTINCT m.id", "mr.system_id=?", "mr.role_id=?",
                "mr.tenant_id IS NULL OR mr.tenant_id=?",
                "mr.valid_from<=CURRENT_TIMESTAMP(3)",
                "mr.valid_until>CURRENT_TIMESTAMP(3)",
                "m.status='ACTIVE'", "m.deleted_at IS NULL",
                "mt.tenant_id=?", "mt.status='ACTIVE'",
                "ORDER BY m.id", "FOR SHARE");
        assertThat(KpiSubjectDirectoryFacade.SubjectResolution.active(
                KpiSubjectDirectoryFacade.SubjectType.ROLE,
                4, "Operators", List.of(30L, 10L, 30L)))
                .extracting(KpiSubjectDirectoryFacade.SubjectResolution::activeMemberIds)
                .isEqualTo(List.of(10L, 30L));
    }

    @Test
    void currentMembershipHidesInactiveDirectoriesAndExpiredRoles() {
        assertThat(KpiSubjectDirectoryBridge.CURRENT_DEPARTMENTS_SQL).contains(
                "md.member_id=?", "md.tenant_id IS NULL OR md.tenant_id=?",
                "md.deleted_at IS NULL", "d.status='ACTIVE'",
                "d.deleted_at IS NULL", "ORDER BY d.id", "FOR SHARE");
        assertThat(KpiSubjectDirectoryBridge.CURRENT_ROLES_SQL).contains(
                "mr.member_id=?", "mr.tenant_id IS NULL OR mr.tenant_id=?",
                "mr.valid_from<=CURRENT_TIMESTAMP(3)",
                "mr.valid_until>CURRENT_TIMESTAMP(3)",
                "r.status='ACTIVE'", "r.deleted_at IS NULL",
                "ORDER BY r.id", "FOR SHARE");
    }

    @Test
    void bothDirectoryOperationsRequireTheCallersTransaction()
            throws Exception {
        assertThat(KpiSubjectDirectoryBridge.class.getMethod(
                "resolve", long.class, long.class,
                KpiSubjectDirectoryFacade.SubjectType.class, long.class)
                .getAnnotation(Transactional.class).propagation().name())
                .isEqualTo("MANDATORY");
        assertThat(KpiSubjectDirectoryBridge.class.getMethod(
                "currentMembership", long.class, long.class, long.class)
                .getAnnotation(Transactional.class).propagation().name())
                .isEqualTo("MANDATORY");
    }
}
