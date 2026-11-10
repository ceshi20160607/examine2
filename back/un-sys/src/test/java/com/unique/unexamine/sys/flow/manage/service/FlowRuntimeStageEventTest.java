package com.unique.unexamine.sys.flow.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.service.IUnFlowActionService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowEventService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskCandidateService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.service.ModulePermissionService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.plat.manage.service.PlatformMessageTemplateService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 业务事件未满足字段条件时，阶段待办仍保持可办理。 */
class FlowRuntimeStageEventTest {
    /** 删除审批通过后来源记录已被逻辑删除，阶段事件不能使审批终态事务回滚。 */
    @Test
    void deletedRecordEventKeepsFlowHistoryWithoutAdvancingTask() {
        var records=mock(IUnModuleRecordService.class);
        var examples=mock(IUnFlowExampleService.class);
        var tasks=mock(IUnFlowTaskService.class);
        var actions=mock(IUnFlowActionService.class);
        var runtime=new FlowRuntimeService(mock(ModulePermissionService.class),records,examples,tasks,
                mock(IUnFlowTaskCandidateService.class),actions,mock(IUnFlowEventService.class),
                new ObjectMapper(),List.of(),mock(ModuleRecordService.class),
                mock(ModuleFlowProcessHandler.class),mock(BusinessEventPublisher.class),
                mock(AuthManageService.class),mock(SystemManageService.class),
                mock(FlowCandidateResolver.class),mock(SystemMessageTemplateService.class),
                mock(PlatformMessageTemplateService.class));
        var example=new UnFlowExample(); example.setId(20L); example.setSystemId(1L);
        example.setTenantId(2L); example.setModuleId(3L); example.setRecordId(4L);
        example.setStatus("RUNNING");
        when(examples.getOne(any(),eq(false))).thenReturn(example);

        runtime.advanceByEvent(20L,40L,new BusinessEventContext("RECORD_DELETE",1L,2L,
                3L,4L,10L,null,Map.of(),Map.of()));

        verifyNoInteractions(actions);
        verify(tasks,never()).addOrUpdate(any());
    }

    @Test
    void unmatchedBusinessConditionDoesNotCompleteStageTask() {
        var records=mock(IUnModuleRecordService.class);
        var examples=mock(IUnFlowExampleService.class);
        var tasks=mock(IUnFlowTaskService.class);
        var actions=mock(IUnFlowActionService.class);
        var runtime=new FlowRuntimeService(mock(ModulePermissionService.class),records,examples,tasks,
                mock(IUnFlowTaskCandidateService.class),actions,mock(IUnFlowEventService.class),
                new ObjectMapper(),List.of(),mock(ModuleRecordService.class),
                mock(ModuleFlowProcessHandler.class),mock(BusinessEventPublisher.class),
                mock(AuthManageService.class),mock(SystemManageService.class),
                mock(FlowCandidateResolver.class),mock(SystemMessageTemplateService.class),
                mock(PlatformMessageTemplateService.class));
        var example=new UnFlowExample(); example.setId(20L); example.setSystemId(1L);
        example.setTenantId(2L); example.setModuleId(3L); example.setRecordId(4L);
        example.setStatus("RUNNING");
        example.setRuntimeSnapshotJson("{\"values\":{\"status\":\"OPEN\"},\"definition\":{"
                + "\"nodes\":[{\"nodeCode\":\"bid\",\"nodeType\":\"STAGE\"}],"
                + "\"edges\":[{\"sourceNodeCode\":\"bid\",\"targetNodeCode\":\"stage_end\","
                + "\"condition\":[{\"fieldKey\":\"__flowEvent\",\"operator\":\"EQ\","
                + "\"value\":\"RECORD_UPDATE\"},{\"fieldKey\":\"status\",\"operator\":\"EQ\","
                + "\"value\":\"DONE\"}]}]}}");
        when(examples.getOne(any(),eq(false))).thenReturn(example);
        var record=new UnModuleRecord(); record.setSystemId(1L); record.setTenantId(2L);
        record.setModuleId(3L);
        when(records.getById(4L)).thenReturn(record);
        var task=new UnFlowTask(); task.setId(40L); task.setExampleId(20L);
        task.setNodeCode("bid"); task.setTaskType("STAGE"); task.setStatus("PENDING");
        task.setNodeSnapshotJson("{\"nodeCode\":\"bid\",\"nodeType\":\"STAGE\",\"manualEnabled\":false}");
        assertEquals(false,ReflectionTestUtils.invokeMethod(runtime,"manualStageEnabled",task));
        when(tasks.getById(40L)).thenReturn(task);

        runtime.advanceByEvent(20L,40L,new BusinessEventContext("RECORD_UPDATE",1L,2L,
                6L,50L,10L,null,Map.of(),Map.of()));

        assertEquals("PENDING",task.getStatus());
        verifyNoInteractions(actions);
        verify(tasks,never()).addOrUpdate(any());
    }
}
