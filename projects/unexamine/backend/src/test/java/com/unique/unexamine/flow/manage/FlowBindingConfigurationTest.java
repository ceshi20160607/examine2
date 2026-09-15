package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.*;
import com.unique.unexamine.flow.base.entity.*;
import com.unique.unexamine.flow.base.service.*;
import com.unique.unexamine.moduleconfig.base.entity.*;
import com.unique.unexamine.moduleconfig.base.service.*;
import com.unique.unexamine.moduleconfig.manage.*;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowBindingConfigurationTest {
    final ObjectMapper json = new ObjectMapper();
    final FlowTriggerBindingBaseService bindings = mock(FlowTriggerBindingBaseService.class);
    final FlowBindingResolutionBaseService resolutions = mock(FlowBindingResolutionBaseService.class);
    final FlowDefinitionBaseService definitions = mock(FlowDefinitionBaseService.class);
    final FlowPublicationBaseService publications = mock(FlowPublicationBaseService.class);
    final FlowVersionBaseService versions = mock(FlowVersionBaseService.class);
    final ConfiguredModuleBaseService modules = mock(ConfiguredModuleBaseService.class);
    final ConfiguredModulePublicationBaseService modulePublications = mock(ConfiguredModulePublicationBaseService.class);
    final ConfiguredModuleActionBaseService actions = mock(ConfiguredModuleActionBaseService.class);
    final SystemTenantBaseService tenants = mock(SystemTenantBaseService.class);
    final ModulePublicationService configurations = mock(ModulePublicationService.class);
    final ChannelFieldPolicyResolver fieldPolicies = mock(ChannelFieldPolicyResolver.class);
    final List<FlowTriggerBinding> stored = new ArrayList<>();
    final AuthenticatedContext actor = new AuthenticatedContext(1L,1L,1L,19L,21L,1L,1L,"admin","管理员","NONE",
            List.of(1L),List.of(new PermissionGrant("*","*","*",List.of(1L))),Map.of());
    FlowBindingService service;
    FlowPublication publication;
    @BeforeEach void setup() throws Exception {
        service = new FlowBindingService(bindings,resolutions,definitions,publications,versions,modules,modulePublications,
                actions,tenants,new PermissionChecker(),mock(AuditRecorder.class),json,configurations,fieldPolicies,new StructuredRuleEvaluator());
        var module = new ConfiguredModule();module.setId(30L);module.setCode("inspection");module.setSystemId(19L);module.setOwnerTenantId(21L);
        when(modules.selectById(30L)).thenReturn(module);
        var mp = new ConfiguredModulePublication();mp.setCurrentVersionId(41L);
        when(modulePublications.selectList(any())).thenReturn(List.of(mp));
        var tenant = new SystemTenant();tenant.setId(21L);
        when(tenants.selectList(any())).thenReturn(List.of(tenant));
        var definition = new FlowDefinition();definition.setId(100L);definition.setSystemId(19L);definition.setOwnerTenantId(21L);definition.setContextType("SYSTEM");
        when(definitions.selectById(100L)).thenReturn(definition);
        publication = new FlowPublication();publication.setCurrentVersionId(101L);
        when(publications.selectList(any())).thenReturn(List.of(publication));
        for(long id: new long[]{101,102}) {
            var version = new FlowVersion();version.setId(id);version.setFlowId(100L);version.setVersionNumber((int)(id-100));version.setSnapshotJson("{\"nodes\":[]}");version.setDefinitionHash("hash"+id);
            when(versions.selectById(id)).thenReturn(version);
        }
        when(bindings.selectList(any())).thenAnswer(invocation -> new ArrayList<>(stored));
        when(bindings.insert(any())).thenAnswer(invocation -> { var row=(FlowTriggerBinding)invocation.getArgument(0);row.setId(200L+stored.size());stored.add(row);return 1; });
        when(bindings.updateById(any())).thenReturn(1);
        when(actions.selectList(any())).thenReturn(List.of());
        when(configurations.published(actor,"inspection")).thenReturn(new RuntimeModuleConfiguration(30L,41L,2,1,json.readTree("""
          {"fields":[{"code":"reading","name":"仪表读数","fieldType":"NUMBER","status":"ACTIVE","configJson":"{}"},
          {"code":"hidden","name":"受限字段","fieldType":"NUMBER","status":"ACTIVE","configJson":"{}"}]}
          """)));
        when(fieldPolicies.resolveIntersection(any(),anyString(),anyString(),anyList(),anyList())).thenReturn(Map.of(
                "reading",new FieldAccessDecision("reading","PAGE∩FLOW",true,true,null,List.of(1L),"可读"),
                "hidden",new FieldAccessDecision("hidden","PAGE∩FLOW",false,false,null,List.of(1L),"拒绝")));
    }
    FlowBindingModels.PublishBindingRequest request(String mode,Long flow,String condition,boolean replace,boolean exclusive) throws Exception {
        return new FlowBindingModels.PublishBindingRequest(30L,null,"CREATE",flow,mode,0,null,json.readTree(condition),exclusive,replace);
    }
    @Test void directDoesNotRequireOrStartAnUnrelatedFlow() throws Exception {
        var view=service.publish(actor,request("DIRECT",null,"{\"mode\":\"ALL\",\"conditions\":[]}",false,true),"trace");
        assertThat(view.flowId()).isNull();assertThat(view.flowVersionId()).isNull();
        var resolved=service.resolveForAction(actor,30L,"CREATE",Map.of(),"trace");
        assertThat(resolved.executionMode()).isEqualTo("DIRECT");assertThat(resolved.flowVersionId()).isNull();
        verify(resolutions,never()).insert(any());
    }
    @Test void publishedBindingPinsFlowVersionAndAnUnmatchedActionFallsBackToNormalWrite() throws Exception {
        var request=request("AFTER_EXECUTION",100L,"{\"field\":\"reading\",\"operator\":\"GT\",\"value\":10}",false,true);
        var view=service.publish(actor,request,"trace");
        assertThat(view.flowVersionId()).isEqualTo(101L);
        publication.setCurrentVersionId(102L);
        var resolution=service.resolveForAction(actor,30L,"CREATE",Map.of("reading",13),"trace");
        assertThat(resolution.flowVersionId()).isEqualTo(101L);assertThat(resolution.flowVersionNumber()).isEqualTo(1);
        assertThat(service.list(actor,30L,"CREATE").currentTenant().getFirst().flowVersionId()).isEqualTo(101L);
        assertThat(service.resolveForAction(actor,30L,"CREATE",Map.of("reading",8),"trace")).isNull();
    }
    @Test void publicationRejectsHiddenReferencesAndWrongTypedValuesBeforeWrites() throws Exception {
        assertThat(service.conditionFields(actor,30L,"CREATE")).extracting(FlowBindingModels.ConditionField::code).containsExactly("reading");
        assertThatThrownBy(()->service.publish(actor,request("AFTER_EXECUTION",100L,"{\"field\":\"hidden\",\"operator\":\"GT\",\"value\":1}",false,true),"trace")).hasMessageContaining("不可读取");
        assertThatThrownBy(()->service.publish(actor,request("AFTER_EXECUTION",100L,"{\"field\":\"reading\",\"operator\":\"GT\",\"value\":\"ten\"}",false,true),"trace")).hasMessageContaining("类型不正确");
        verify(bindings,never()).insert(any());
        assertThatThrownBy(()->service.previewCondition(actor,new FlowBindingModels.PreviewConditionRequest(30L,"CREATE",json.readTree("{\"mode\":\"ALL\",\"conditions\":[]}"),Map.of("hidden",1)))).hasMessageContaining("不可读取");
    }
    @Test void repeatPublicationIsStableAndPolicyChangesRequireExplicitReplacement() throws Exception {
        var input=request("AFTER_APPROVAL",100L,"{\"mode\":\"ALL\",\"conditions\":[]}",false,true);
        var first=service.publish(actor,input,"trace");
        assertThat(service.publish(actor,input,"trace").id()).isEqualTo(first.id());assertThat(stored).hasSize(1);
        assertThatThrownBy(()->service.publish(actor,request("FLOW_ONLY",100L,"{\"mode\":\"ALL\",\"conditions\":[]}",false,false),"trace")).hasMessageContaining("确认替换");
        service.publish(actor,request("FLOW_ONLY",100L,"{\"mode\":\"ALL\",\"conditions\":[]}",true,false),"trace");
        assertThat(stored).hasSize(2);assertThat(stored.getFirst().getStatus()).isEqualTo("DISABLED");assertThat(stored.getLast().getStatus()).isEqualTo("ACTIVE");
        assertThat(service.resolveForAction(actor,30L,"CREATE",Map.of(),"trace").executionMode()).isEqualTo("FLOW_ONLY");
    }
}
