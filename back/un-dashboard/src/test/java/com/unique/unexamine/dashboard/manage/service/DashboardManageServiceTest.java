package com.unique.unexamine.dashboard.manage.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDashboard;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardUserService;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.AccessRequest;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.AccessSubject;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 仪表盘授权必须使用当前租户的有效成员关系。 */
class DashboardManageServiceTest {
    private final IUnDashboardService dashboards = mock(IUnDashboardService.class);
    private final IUnDashboardComponentService components = mock(IUnDashboardComponentService.class);
    private final IUnDashboardUserService dashboardUsers = mock(IUnDashboardUserService.class);
    private final IUnSysMemberService members = mock(IUnSysMemberService.class);
    private final IUnSysTenantMemberService tenantMembers = mock(IUnSysTenantMemberService.class);
    private final SystemManageService systems = mock(SystemManageService.class);
    private final DashboardManageService service = new DashboardManageService(dashboards, components,
            mock(IUnSysLogOptService.class), dashboardUsers, mock(IUnDataSourceService.class),
            mock(IUnPlatAccountService.class), members, tenantMembers,
            mock(IUnSysDepartmentService.class), mock(IUnSysRoleService.class), systems,
            mock(ModuleConfigService.class), mock(DashboardRuntimeService.class), new ObjectMapper());

    /** 同系统但不属于当前租户的成员不能进入授权名单，原有名单也不能被清空。 */
    @Test
    void rejectsSystemMemberOutsideCurrentTenantBeforeReplacingAccess() {
        prepare();

        assertThrows(BaseException.class, () -> service.saveAccess("token", 1L, 2L, 3L,
                new AccessRequest(List.of(new AccessSubject("MEMBER", 7L)))));

        verify(dashboardUsers, never()).remove(any());
    }

    /** 成员在当前租户有效时允许保存授权。 */
    @Test
    void acceptsActiveSystemMemberInCurrentTenant() {
        prepare();
        when(tenantMembers.count(any())).thenReturn(1L);
        when(components.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<UnDashboardComponent>>any()))
                .thenReturn(List.of());

        assertNotNull(service.saveAccess("token", 1L, 2L, 3L,
                new AccessRequest(List.of(new AccessSubject("MEMBER", 7L)))));

        verify(dashboardUsers).remove(any());
        verify(dashboardUsers).addOrUpdate(any());
    }

    private void prepare() {
        when(systems.context("token", 1L, 2L))
                .thenReturn(new SystemContext(1L, 2L, 4L, 5L, 6L, true, true));
        UnDashboard dashboard = new UnDashboard();
        dashboard.setId(3L);
        dashboard.setSystemId(1L);
        dashboard.setTenantId(2L);
        dashboard.setScopeType("TENANT");
        when(dashboards.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(dashboard);
        UnSysMember member = new UnSysMember();
        member.setId(7L);
        member.setSystemId(1L);
        member.setStatus("ACTIVE");
        when(members.queryById(7L)).thenReturn(member);
    }
}
