package com.unique.examine.plat.manage.controller;

import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SystemAdminReportingApiContractTest {
    @Test
    void exposesExplicitLeaderAndManagerMaintenanceUnderExistingPermissions() {
        assertThat(SystemAdminController.ORGANIZATION_PERMISSION)
                .isEqualTo("system.organization.manage");
        assertThat(SystemAdminController.MEMBER_PERMISSION)
                .isEqualTo("system.member.manage");
        assertRoute("updateDepartmentLeader", "/departments/{departmentId}/leader");
        assertRoute("updateMemberManager", "/members/{memberId}/manager");
    }

    @Test
    void nullableTargetMakesClearExplicitWhileParentVersionRemainsRequired() {
        assertThat(new SystemAdminRequests.UpdateDepartmentLeader(null, "7").leaderMemberId())
                .isNull();
        assertThat(new SystemAdminRequests.UpdateMemberManager(null, "8").managerMemberId())
                .isNull();
        assertThat(Arrays.stream(SystemAdminRequests.UpdateDepartmentLeader.class.getRecordComponents())
                .map(component -> component.getName())).containsExactly("leaderMemberId", "version");
        assertThat(Arrays.stream(SystemAdminRequests.UpdateMemberManager.class.getRecordComponents())
                .map(component -> component.getName())).containsExactly("managerMemberId", "version");
    }

    @Test
    void organizationViewsExposeCurrentRelationships() {
        assertThat(Arrays.stream(SystemAdminViews.Department.class.getRecordComponents())
                .map(component -> component.getName())).contains("leaderMemberId", "version");
        assertThat(Arrays.stream(SystemAdminViews.Member.class.getRecordComponents())
                .map(component -> component.getName())).contains("managerMemberId", "version");
    }

    private static void assertRoute(String methodName, String route) {
        var method = Arrays.stream(SystemAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly(route);
    }
}
