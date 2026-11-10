package com.unique.unexamine.work.manage.service;

import com.unique.unexamine.work.manage.dto.WorkTaskDto;
import com.unique.unexamine.plat.manage.service.PlatformMessageTemplateService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.entity.UnWorkTask;
import com.unique.unexamine.work.base.entity.UnWorkTaskHistory;
import com.unique.unexamine.work.base.service.IUnWorkTaskHistoryService;
import com.unique.unexamine.work.base.service.IUnWorkTaskMemberService;
import com.unique.unexamine.work.base.service.IUnWorkTaskService;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 项目任务复用基础任务持久化，同时遵守项目归属和成员授权。 */
class WorkTaskServiceTest {
    private final WorkScopeService scopes=mock(WorkScopeService.class);
    private final WorkProjectService projects=mock(WorkProjectService.class);
    private final IUnWorkTaskService tasks=mock(IUnWorkTaskService.class);
    private final IUnWorkTaskHistoryService histories=mock(IUnWorkTaskHistoryService.class);
    private final IUnWorkTaskMemberService members=mock(IUnWorkTaskMemberService.class);
    private final PlatformMessageTemplateService platformMessages=mock(PlatformMessageTemplateService.class);
    private final SystemMessageTemplateService systemMessages=mock(SystemMessageTemplateService.class);
    private final WorkTaskService service=new WorkTaskService(scopes,projects,tasks,histories,
            members,platformMessages,systemMessages,new ObjectMapper());

    @Test
    void projectTaskKeepsItsProjectGroupAndHistory() {
        Scope scope=new Scope(10L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkProject project=new UnWorkProject();
        project.setId(20L);project.setOwnerAccountId(10L);
        when(projects.visible(scope,20L,true)).thenReturn(project);
        when(tasks.addOrUpdate(any(UnWorkTask.class))).thenAnswer(invocation->{
            UnWorkTask row=invocation.getArgument(0);row.setId(40L);return row;
        });
        var result=service.createProjectTask("token",null,null,20L,
                new WorkTaskDto.ProjectCreateRequest("交付测试",null,null,30L,null));
        assertEquals(20L,result.projectId());
        assertEquals(30L,result.taskGroupId());
        assertEquals(10L,result.ownerAccountId());
        verify(projects).requireOwner(scope,project);
        verify(projects).requireGroup(20L,30L);
        verify(histories).addOrUpdate(any(UnWorkTaskHistory.class));
        verify(projects).refreshProgress(20L,30L);
    }

    @Test
    void assignedProjectTaskNotifiesNewOwnerAfterHistoryIsSaved() {
        Scope scope=new Scope(10L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkProject project=new UnWorkProject();
        project.setId(20L);project.setOwnerAccountId(10L);
        when(projects.visible(scope,20L,true)).thenReturn(project);
        when(tasks.addOrUpdate(any(UnWorkTask.class))).thenAnswer(invocation->{
            UnWorkTask row=invocation.getArgument(0);row.setId(40L);return row;
        });
        when(histories.addOrUpdate(any(UnWorkTaskHistory.class))).thenAnswer(invocation->{
            UnWorkTaskHistory row=invocation.getArgument(0);row.setId(50L);return row;
        });
        service.createProjectTask("token",null,null,20L,
                new WorkTaskDto.ProjectCreateRequest("指定负责人",null,null,30L,12L));
        verify(projects).requireMember(project,12L);
        verify(platformMessages).taskAssigned("token",12L,40L,50L,"指定负责人");
    }

    @Test
    void projectTaskDetailRequiresProjectVisibility() {
        Scope scope=new Scope(11L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkTask task=new UnWorkTask();
        task.setId(40L);task.setProjectId(20L);
        when(tasks.getOne(any(),eq(false))).thenReturn(task);
        when(projects.visible(scope,20L,false))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"项目不存在"));
        assertThrows(ResponseStatusException.class,
                ()->service.queryById("token",null,null,40L));
        verify(projects).visible(scope,20L,false);
    }
}
