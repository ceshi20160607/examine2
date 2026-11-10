package com.unique.unexamine.sys.flow.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.FlowView;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.VersionView;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.BindingView;
import com.unique.unexamine.sys.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowBindingService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.Route;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.SaveRequest;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.Stage;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;
import com.unique.unexamine.core.field.FieldValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;

/** 阶段线路的审批结果由 Flow 快照条件驱动，拒绝和取消不会误走通过分支。 */
class ModuleStageServiceTest {
    @Test
    void configuredEventRouteMatchesEventAndKeepsManualFallback() {
        ObjectMapper json=new ObjectMapper();
        var service=new ModuleStageService(mock(SystemManageService.class),mock(IUnModuleService.class),
                mock(IUnModuleTenantService.class),mock(IUnSysMemberService.class),
                mock(IUnSysTenantMemberService.class),mock(IUnFlowService.class),
                mock(IUnModuleFlowService.class),mock(IUnFlowBindingService.class),
                mock(IUnModuleRelationConfigService.class),mock(FlowDefinitionService.class),
                mock(FlowBindingService.class),json,mock(ModuleConfigService.class),
                mock(ModuleFieldService.class),mock(FieldValidationService.class),mock(IUnSysLogOptService.class));
        var request=new SaveRequest("商机阶段","补充完成后推进","CREATE",null,null,
                List.of(new Stage("understand","了解",false,List.of(10L),List.of(20L),null),
                        new Stage("bid","投标",true,List.of(10L),List.of(),null)),
                List.of(new Route("understand","bid",null,"RECORD_UPDATE",
                                json.valueToTree(Map.of("fieldKey","status","operator","EQ","value","DONE")),0,true),
                        new Route("understand","bid",null,null,null,1,true),
                        new Route("bid",null,null,null,null,0,true)));
        JsonNode snapshot=ReflectionTestUtils.invokeMethod(service,"snapshot",request);
        assertFalse(snapshot.path("nodes").get(1).path("manualEnabled").asBoolean());
        JsonNode eventCondition=snapshot.path("edges").get(1).path("condition");
        assertTrue(TriggerConditionMatcher.matches(json,eventCondition.toString(),
                Map.of("__flowEvent","RECORD_UPDATE","status","DONE")));
        assertFalse(TriggerConditionMatcher.matches(json,eventCondition.toString(),
                Map.of("__flowEvent","RECORD_CREATE","status","DONE")));
        assertFalse(TriggerConditionMatcher.matches(json,eventCondition.toString(),
                Map.of("__flowEvent","RECORD_UPDATE","status","OPEN")));
        assertEquals(request.routes(),((SaveRequest)ReflectionTestUtils.invokeMethod(service,
                "decode","商机阶段","补充完成后推进",snapshot,null)).routes());
    }

    @Test
    void stageApprovalRoutesOnlyOnConfiguredOutcomeAndCanBeReadBack() {
        ObjectMapper json=new ObjectMapper();
        var service=new ModuleStageService(mock(SystemManageService.class),mock(IUnModuleService.class),
                mock(IUnModuleTenantService.class),mock(IUnSysMemberService.class),
                mock(IUnSysTenantMemberService.class),mock(IUnFlowService.class),
                mock(IUnModuleFlowService.class),mock(IUnFlowBindingService.class),
                mock(IUnModuleRelationConfigService.class),mock(FlowDefinitionService.class),
                mock(FlowBindingService.class),json,mock(ModuleConfigService.class),
                mock(ModuleFieldService.class),mock(FieldValidationService.class),mock(IUnSysLogOptService.class));
        var request=new SaveRequest("商机阶段","按阶段补充资料","CREATE",null,null,
                List.of(new Stage("understand","了解",true,List.of(10L),List.of(20L),null),
                        new Stage("bid","投标",true,List.of(10L),List.of(21L),30L)),
                List.of(new Route("understand","bid",null,null,null,0,true),
                        new Route("bid",null,"COMPLETED",null,null,0,true),
                        new Route("bid","bid","REJECTED",null,null,1,true),
                        new Route("bid","bid","CANCELLED",null,null,2,true)));
        JsonNode snapshot=ReflectionTestUtils.invokeMethod(service,"snapshot",request);
        assertNotNull(snapshot);
        assertEquals(5,snapshot.path("nodes").size());
        JsonNode completed=null,rejected=null;
        for (JsonNode edge:snapshot.path("edges")) {
            if (!"bid_approval".equals(edge.path("sourceNodeCode").asText())) continue;
            if ("stage_end".equals(edge.path("targetNodeCode").asText())) completed=edge.path("condition");
            if ("bid".equals(edge.path("targetNodeCode").asText())
                    && "REJECTED".equals(edge.path("condition").path(0).path("value").asText()))
                rejected=edge.path("condition");
        }
        assertNotNull(completed); assertNotNull(rejected);
        assertTrue(TriggerConditionMatcher.matches(json,completed.toString(),Map.of("__examineStatus","COMPLETED")));
        assertFalse(TriggerConditionMatcher.matches(json,completed.toString(),Map.of("__examineStatus","REJECTED")));
        assertTrue(TriggerConditionMatcher.matches(json,rejected.toString(),Map.of("__examineStatus","REJECTED")));
        assertFalse(TriggerConditionMatcher.matches(json,rejected.toString(),Map.of("__examineStatus","CANCELLED")));
        SaveRequest decoded=ReflectionTestUtils.invokeMethod(service,"decode","商机阶段","按阶段补充资料",snapshot,null);
        assertEquals(request.stages(),decoded.stages());
        assertEquals(request.routes(),decoded.routes());
    }

    @Test
    void savePublishesFlowAndStoresRelatedModulesForItsVersion() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnFlowBinding.class);
        var systems=mock(SystemManageService.class);
        var modules=mock(IUnModuleService.class);
        var tenantModules=mock(IUnModuleTenantService.class);
        var members=mock(IUnSysMemberService.class);
        var tenantMembers=mock(IUnSysTenantMemberService.class);
        var flows=mock(IUnFlowService.class);
        var moduleFlows=mock(IUnModuleFlowService.class);
        var bindings=mock(IUnFlowBindingService.class);
        var related=mock(IUnModuleRelationConfigService.class);
        var definitions=mock(FlowDefinitionService.class);
        var flowBindings=mock(FlowBindingService.class);
        var moduleConfig=mock(ModuleConfigService.class);
        var logs=mock(IUnSysLogOptService.class);
        var service=new ModuleStageService(systems,modules,tenantModules,members,tenantMembers,
                flows,moduleFlows,bindings,related,definitions,flowBindings,new ObjectMapper(),
                moduleConfig,mock(ModuleFieldService.class),mock(FieldValidationService.class),logs);
        when(systems.requireAdminContext("token",1L,2L))
                .thenReturn(new SystemManageDto.SystemContext(1L,2L,10L,11L,12L,true,false));
        var module=new UnModule(); module.setId(3L); module.setSystemId(1L);
        module.setTenantId(2L); module.setStatus("ACTIVE");
        when(modules.getOne(any(),eq(false))).thenReturn(module);
        var target=new UnModule(); target.setId(6L); target.setSystemId(1L); target.setStatus("ACTIVE");
        when(modules.getById(6L)).thenReturn(target);
        var member=new UnSysMember(); member.setId(11L);
        when(members.getOne(any(),eq(false))).thenReturn(member);
        when(tenantMembers.count(any())).thenReturn(1L);
        when(tenantModules.count(any())).thenReturn(1L);
        when(moduleConfig.currentFields(3L)).thenReturn(List.of());
        when(definitions.save(eq("token"),eq(1L),eq(2L),any()))
                .thenReturn(new FlowView(20L,"SYSTEM",1L,2L,"module_3_stages","商机阶段",
                        null,"ACTIVE",1,null,null,List.of(new VersionView(30L,1,"hash",10L,null))));
        when(flowBindings.save(eq("token"),eq(1L),eq(2L),any()))
                .thenReturn(new BindingView(40L,"SYSTEM",1L,2L,3L,null,"CREATE",20L,
                        30L,"AFTER_SAVE",0,true,null,"ACTIVE",1,null));
        when(moduleFlows.addOrUpdate(any())).thenAnswer(call->{
            var link=(com.unique.unexamine.sys.flow.base.entity.UnModuleFlow)call.getArgument(0);
            link.setId(50L); return link;
        });
        var request=new SaveRequest("商机阶段",null,"CREATE",null,null,
                List.of(new Stage("understand","了解",true,List.of(10L),List.of(6L),null)),
                List.of(new Route("understand",null,null,null,null,0,true)));

        var result=service.save("token",1L,2L,3L,request);
        assertEquals(30L,result.flowVersionId());
        verify(related).addOrUpdate(argThat((UnModuleRelationConfig row)->
                row.getTargetModuleId().equals(6L)
                        && row.getConfigJson().contains("\"flowVersionId\":30")
                        && row.getConfigJson().contains("\"stageCode\":\"understand\"")));
        verify(logs).addOrUpdate(any());
        verify(moduleFlows,never()).update(any());
    }
}
