package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ModuleRecordAccess;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskCandidateService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordAccessAdapter;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只有当前阶段的待办管理人可关联该版本允许的补充模块记录。 */
class ModuleStageRecordServiceTest {
    @Test
    void currentManagerCanAttachConfiguredModuleButOtherUserCannot() {
        var mapper=new MapperBuilderAssistant(new Configuration(),"");
        for (Class<?> type:List.of(UnFlowExample.class,UnFlowTask.class,UnFlowTaskCandidate.class,
                UnModuleFlow.class,UnModuleRelationConfig.class,UnModuleRelation.class))
            TableInfoHelper.initTableInfo(mapper,type);
        var access=mock(ModuleRecordAccessAdapter.class);
        var examples=mock(IUnFlowExampleService.class);
        var tasks=mock(IUnFlowTaskService.class);
        var candidates=mock(IUnFlowTaskCandidateService.class);
        var moduleFlows=mock(IUnModuleFlowService.class);
        var versions=mock(IUnFlowVersionService.class);
        var records=mock(IUnModuleRecordService.class);
        var configs=mock(IUnModuleRelationConfigService.class);
        var relations=mock(IUnModuleRelationService.class);
        var logs=mock(IUnModuleLogService.class);
        var service=new ModuleStageRecordService(access,mock(FlowRuntimeService.class),moduleFlows,
                examples,versions,tasks,candidates,records,configs,relations,
                logs,new ObjectMapper());
        when(access.require("token",1L,2L,3L,4L))
                .thenReturn(new ModuleRecordAccess.Access(1L,2L,10L,false,false));
        when(access.require("token",1L,2L,6L,50L))
                .thenReturn(new ModuleRecordAccess.Access(1L,2L,10L,false,false));
        var example=new UnFlowExample(); example.setId(20L); example.setFlowId(21L);
        example.setFlowVersionId(30L); example.setSystemId(1L); example.setTenantId(2L);
        example.setModuleId(3L); example.setRecordId(4L); example.setStatus("RUNNING");
        when(examples.getOne(any(),eq(false))).thenReturn(example);
        when(moduleFlows.count(any())).thenReturn(1L);
        var version=new UnFlowVersion(); version.setFlowId(21L);
        version.setSnapshotJson("{\"nodes\":[{\"nodeCode\":\"stage_start\",\"nodeType\":\"START\"}]}");
        when(versions.getById(30L)).thenReturn(version);
        var task=new UnFlowTask(); task.setId(40L); task.setTaskType("STAGE"); task.setStatus("PENDING");
        when(tasks.getOne(any(),eq(false))).thenReturn(task);
        when(candidates.count(any())).thenReturn(1L);
        var target=new UnModuleRecord(); target.setId(50L); target.setSystemId(1L);
        target.setTenantId(2L); target.setModuleId(6L); target.setName("投标资料");
        when(records.getById(50L)).thenReturn(target);
        var config=new UnModuleRelationConfig(); config.setId(60L); config.setTargetModuleId(6L);
        config.setConfigJson("{\"flowVersionId\":30,\"stageCode\":\"bid\"}");
        when(configs.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(config));
        when(relations.addOrUpdate(any())).thenAnswer(call->{
            UnModuleRelation row=call.getArgument(0); row.setId(70L); return row;
        });

        var linked=service.attach("token",1L,2L,3L,4L,20L,"bid",50L);
        assertEquals(70L,linked.relationId());
        assertEquals(6L,linked.moduleId());
        verify(relations).addOrUpdate(argThat(row->row.getRelationConfigId().equals(60L)
                && "ACTIVE".equals(row.getStatus())));
        verify(logs).addOrUpdate(any());

        when(candidates.count(any())).thenReturn(0L);
        assertThrows(BaseException.class,()->service.attach("token",1L,2L,3L,4L,20L,"bid",50L));
        verify(relations,times(1)).addOrUpdate(any());
    }
}
