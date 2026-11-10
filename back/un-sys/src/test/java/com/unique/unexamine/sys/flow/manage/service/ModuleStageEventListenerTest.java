package com.unique.unexamine.sys.flow.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 补充记录事件只送达同一版本、同一阶段的运行实例。 */
class ModuleStageEventListenerTest {
    @Test
    void relatedRecordEventAdvancesOnlyItsConfiguredStage() {
        var examples=mock(IUnFlowExampleService.class);
        var flows=mock(IUnFlowService.class);
        var tasks=mock(IUnFlowTaskService.class);
        var moduleFlows=mock(IUnModuleFlowService.class);
        var relations=mock(IUnModuleRelationService.class);
        var configs=mock(IUnModuleRelationConfigService.class);
        var runtime=mock(FlowRuntimeService.class);
        @SuppressWarnings("unchecked") ObjectProvider<FlowRuntimeService> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(runtime);
        var listener=new ModuleStageEventListener(examples,flows,tasks,moduleFlows,relations,configs,
                new ObjectMapper(),provider);
        var relation=new UnModuleRelation(); relation.setSourceRecordId(4L);
        relation.setTargetRecordId(50L); relation.setRelationConfigId(60L);
        when(relations.list(any(Wrapper.class))).thenReturn(List.of(relation));
        var config=new UnModuleRelationConfig(); config.setSystemId(1L); config.setTenantId(2L);
        config.setSourceModuleId(3L); config.setTargetModuleId(6L); config.setRelationType("STAGE");
        config.setConfigJson("{\"flowVersionId\":30,\"stageCode\":\"bid\"}");
        when(configs.getById(60L)).thenReturn(config);
        var example=new UnFlowExample(); example.setId(20L); example.setSystemId(1L);
        example.setTenantId(2L); example.setModuleId(3L); example.setFlowId(21L);
        when(examples.list(any(Wrapper.class))).thenReturn(List.of(),List.of(example));
        var flow=new UnFlow(); flow.setSystemId(1L); flow.setTenantId(2L);
        flow.setCode("module_3_stages");
        when(flows.getById(21L)).thenReturn(flow);
        when(moduleFlows.count(any())).thenReturn(1L);
        var bid=new UnFlowTask(); bid.setId(40L); bid.setNodeCode("bid");
        var other=new UnFlowTask(); other.setId(41L); other.setNodeCode("understand");
        when(tasks.list(any(Wrapper.class))).thenReturn(List.of(bid,other));
        var event=new BusinessEventContext("RECORD_UPDATE",1L,2L,6L,50L,10L,null,
                Map.of(),Map.of());

        listener.onEvent(event);

        verify(runtime).advanceByEvent(20L,40L,event);
        verifyNoMoreInteractions(runtime);
    }
}
