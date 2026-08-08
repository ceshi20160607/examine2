package com.unique.examine.plat.base;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.unique.examine.plat.base.entity.DepartmentLeaderAssignment;
import com.unique.examine.plat.base.entity.MemberManagerAssignment;
import com.unique.examine.plat.base.mapper.PlatDepartmentLeaderAssignmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberManagerAssignmentMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingAssignmentPersistenceContractTest {
    @Test
    void assignmentsMapTheDurableHistoryTablesAndAuditFields() throws Exception {
        assertThat(MemberManagerAssignment.class.getAnnotation(TableName.class).value())
                .isEqualTo("un_plat_member_manager_assignment");
        assertThat(DepartmentLeaderAssignment.class.getAnnotation(TableName.class).value())
                .isEqualTo("un_plat_department_leader_assignment");

        assertVersioned(MemberManagerAssignment.class);
        assertVersioned(DepartmentLeaderAssignment.class);

        var manager = new MemberManagerAssignment();
        manager.setAssignedBy(90L);
        manager.setAssignedAt(LocalDateTime.parse("2026-07-30T20:00:00"));
        manager.setClearedBy(91L);
        manager.setClearedAt(LocalDateTime.parse("2026-07-30T21:00:00"));
        assertThat(manager.getAssignedBy()).isEqualTo(90L);
        assertThat(manager.getAssignedAt()).isNotNull();
        assertThat(manager.getClearedBy()).isEqualTo(91L);
        assertThat(manager.getClearedAt()).isAfter(manager.getAssignedAt());
    }

    @Test
    void clearIsExplicitAuditedAndOptimistic() throws Exception {
        assertClearContract(PlatMemberManagerAssignmentMapper.class);
        assertClearContract(PlatDepartmentLeaderAssignmentMapper.class);
    }

    @Test
    void managerWritesLockTheTenantBeforeCheckingTheWholeReportingGraph() throws Exception {
        var method = PlatMemberManagerAssignmentMapper.class.getMethod(
                "lockTenant",
                long.class,
                long.class
        );
        assertThat(String.join(" ", method.getAnnotation(Select.class).value()))
                .contains("FROM un_plat_tenant")
                .contains("system_id = #{systemId}")
                .contains("id = #{tenantId}")
                .contains("FOR UPDATE");
    }

    private static void assertVersioned(Class<?> type) throws Exception {
        assertThat(type.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
    }

    private static void assertClearContract(Class<?> mapper) {
        Method method = java.util.Arrays.stream(mapper.getMethods())
                .filter(candidate -> candidate.getName().equals("clearActive"))
                .findFirst()
                .orElseThrow();
        var sql = String.join(" ", method.getAnnotation(Update.class).value());
        assertThat(sql)
                .contains("status = 'CLEARED'")
                .contains("cleared_by = #{clearedBy}")
                .contains("cleared_at = #{clearedAt}")
                .contains("version = version + 1")
                .contains("status = 'ACTIVE'")
                .contains("version = #{expectedVersion}");
    }
}
