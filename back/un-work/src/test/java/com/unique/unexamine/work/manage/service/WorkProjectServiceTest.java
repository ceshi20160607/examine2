package com.unique.unexamine.work.manage.service;

import com.unique.unexamine.work.manage.dto.WorkProjectDto;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.entity.UnWorkProjectMember;
import com.unique.unexamine.work.base.entity.UnWorkTaskGroup;
import com.unique.unexamine.work.base.service.IUnWorkProjectMemberService;
import com.unique.unexamine.work.base.service.IUnWorkProjectService;
import com.unique.unexamine.work.base.service.IUnWorkTaskGroupService;
import com.unique.unexamine.work.base.service.IUnWorkTaskService;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 项目负责人修改任务组状态、负责人和显示顺序时保持项目归属。 */
class WorkProjectServiceTest {
    @Test
    void ownerCanUpdateGroupWithinProject() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),
                UnWorkTaskGroup.class);
        WorkScopeService scopes=mock(WorkScopeService.class);
        IUnWorkProjectService projects=mock(IUnWorkProjectService.class);
        IUnWorkProjectMemberService members=mock(IUnWorkProjectMemberService.class);
        IUnWorkTaskGroupService groups=mock(IUnWorkTaskGroupService.class);
        WorkProjectService service=new WorkProjectService(scopes,projects,members,groups,
                mock(IUnWorkTaskService.class));
        Scope scope=new Scope(10L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkProject project=new UnWorkProject();
        project.setId(20L);project.setOwnerAccountId(10L);project.setStatus("ACTIVE");
        when(projects.getOne(any(),eq(false))).thenReturn(project);
        UnWorkTaskGroup group=new UnWorkTaskGroup();
        group.setId(30L);group.setProjectId(20L);group.setStatus("ACTIVE");group.setSortNo(1);
        when(groups.getOne(any(),eq(false))).thenReturn(group);
        when(members.exists(any())).thenReturn(true);
        var result=service.editGroup("token",null,null,20L,30L,
                new WorkProjectDto.GroupEditRequest("测试阶段",11L,"PAUSED",2,null));
        assertEquals(20L,result.projectId());
        assertEquals(11L,result.ownerAccountId());
        assertEquals("PAUSED",result.status());
        assertEquals(2,result.sortNo());
    }

    @Test
    void lastProjectMembershipCannotBeRemovedBeforeTaskTransfer() {
        WorkScopeService scopes=mock(WorkScopeService.class);
        IUnWorkProjectService projects=mock(IUnWorkProjectService.class);
        IUnWorkProjectMemberService members=mock(IUnWorkProjectMemberService.class);
        IUnWorkTaskService tasks=mock(IUnWorkTaskService.class);
        WorkProjectService service=new WorkProjectService(scopes,projects,members,
                mock(IUnWorkTaskGroupService.class),tasks);
        Scope scope=new Scope(10L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkProject project=new UnWorkProject();
        project.setId(20L);project.setOwnerAccountId(10L);
        when(projects.getOne(any(),eq(false))).thenReturn(project);
        UnWorkProjectMember member=new UnWorkProjectMember();
        member.setId(30L);member.setProjectId(20L);member.setAccountId(11L);
        member.setMemberType("COLLABORATOR");member.setStatus("ACTIVE");
        when(members.getOne(any(),eq(false))).thenReturn(member);
        when(members.count(any())).thenReturn(0L);
        when(tasks.exists(any())).thenReturn(true);
        BaseException error = assertThrows(BaseException.class,
                ()->service.removeMember("token",null,null,20L,30L));
        assertEquals(SystemCodeEnum.SYSTEM_CONFLICT.getCode(), error.getCode());
        verify(members,never()).update(any());
    }
}
