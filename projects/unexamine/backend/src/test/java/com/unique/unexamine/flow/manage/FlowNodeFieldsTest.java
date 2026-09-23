package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 校验节点权限缺省、发布冲突及冻结身份，防止配置失效时扩大读取或接受错误编辑集合。 */
class FlowNodeFieldsTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void absentVisibleAndExplicitEmptyHaveDifferentMeaning() {
        assertTrue(FlowNodeFieldPolicy.allowsRead(Map.of(), "subject"));
        assertFalse(FlowNodeFieldPolicy.allowsEdit(Map.of(), "subject"));
        assertFalse(FlowNodeFieldPolicy.allowsRead(Map.of("visible", List.of()), "subject"));
        assertFalse(FlowNodeFieldPolicy.allowsRead(Map.of("visible", List.of("subject"), "hidden", List.of("subject")), "subject"));
    }

    @Test
    void malformedCollectionsFailClosed() {
        assertFalse(FlowNodeFieldPolicy.allowsRead(Map.of("hidden", "subject"), "score"));
        assertFalse(FlowNodeFieldPolicy.allowsRead(Map.of("editable", List.of(17)), "subject"));
        assertFalse(FlowNodeFieldPolicy.inspect(Map.of("visible", "subject"), Set.of("subject"), Set.of("subject")).isEmpty());
    }

    @Test
    void publishRejectsUnknownDuplicatesAndContradictoryEdits() {
        assertFalse(FlowNodeFieldPolicy.inspect(Map.of("hidden", List.of("gone", "gone")), Set.of("subject"), Set.of("subject")).isEmpty());
        assertFalse(FlowNodeFieldPolicy.inspect(Map.of("hidden", List.of("subject"), "editable", List.of("subject")), Set.of("subject"), Set.of("subject")).isEmpty());
        assertFalse(FlowNodeFieldPolicy.inspect(Map.of("required", List.of("subject")), Set.of("subject"), Set.of("subject")).isEmpty());
        assertFalse(FlowNodeFieldPolicy.inspect(Map.of("editable", List.of("subject")), Set.of("subject"), Set.of()).isEmpty());
        assertTrue(FlowNodeFieldPolicy.inspect(Map.of("editable", List.of("subject"), "required", List.of("subject"), "otherSetting", true), Set.of("subject"), Set.of("subject")).isEmpty());
    }

    @Test
    void frozenBindingsRejectRecreatedCodesTypesAndChangedSources() {
        var binding = Map.of("id", 177, "code", "subject", "fieldType", "SINGLE_SELECT", "dictionaryId", 11, "referenceModuleId", 51);
        Map<String, Object> policy = Map.of("fieldBindings", List.of(binding));
        assertTrue(FlowNodeFieldPolicy.matchesBinding(policy, 177L, "subject", "SINGLE_SELECT", 11L, 51L));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(policy, 178L, "subject", "SINGLE_SELECT", 11L, 51L));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(policy, 177L, "subject", "TEXT", 11L, 51L));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(policy, 177L, "subject", "SINGLE_SELECT", 12L, 51L));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(policy, 177L, "subject", "SINGLE_SELECT", 11L, 52L));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(Map.of("fieldBindings", List.of()), 177L, "subject", "TEXT", null, null));
        assertTrue(FlowNodeFieldPolicy.matchesBinding(Map.of(), 177L, "subject", "TEXT", null, null));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(Map.of("fieldBindings", List.of(Map.of("id", 177.5, "code", "subject", "fieldType", "TEXT"))), 177L, "subject", "TEXT", null, null));
        assertFalse(FlowNodeFieldPolicy.matchesBinding(Map.of("fieldBindings", List.of(Map.of("id", 177, "code", "subject", "fieldType", "TEXT", "dictionaryId", "invalid"))), 177L, "subject", "TEXT", null, null));
    }

    @Test
    void safeDirectoryIncludesAllTypesButExcludesUnreadableAndRawConfiguration() throws Exception {
        var conditions = mock(FlowConditionService.class);
        var policies = mock(ChannelFieldPolicyResolver.class);
        var actor = mock(AuthenticatedContext.class);
        when(conditions.businessPublication(actor, 51L)).thenReturn(publication());
        when(policies.resolveIntersection(eq(actor), eq("module_test"), eq("LIST"), eq(List.of("PAGE", "FLOW")), anyList()))
                .thenReturn(Map.of("subject", decision("subject", true), "secret", decision("secret", true), "blocked", decision("blocked", false)));
        var service = new FlowNodeFormService(conditions, policies, json);
        var fields = service.fields(actor, 51L);
        assertEquals(List.of("subject", "secret"), fields.stream().map(FlowDesignModels.NodeFormField::code).toList());
        assertFalse(fields.getFirst().editable());
        assertEquals("SECRET", fields.get(1).fieldType());
        assertFalse(json.writeValueAsString(fields).contains("private_default"));
        assertFalse(json.writeValueAsString(fields).contains("configJson"));
    }

    @Test
    void missingBusinessModuleCannotPublishDeclaredPolicy() {
        var service = new FlowNodeFormService(mock(FlowConditionService.class), mock(ChannelFieldPolicyResolver.class), json);
        var issues = service.inspect(mock(AuthenticatedContext.class), List.of(node("APPROVAL", "{}", "{\"hidden\":[]}")));
        assertEquals("FLOW_NODE_FIELDS_INVALID", issues.getFirst().code());
    }

    @Test
    void snapshotCapturesSafeIdentityAndReadonlyEditIsRejected() throws Exception {
        var conditions = mock(FlowConditionService.class);
        var policies = mock(ChannelFieldPolicyResolver.class);
        var actor = mock(AuthenticatedContext.class);
        when(conditions.businessPublication(actor, 51L)).thenReturn(publication());
        when(policies.resolveIntersection(eq(actor), anyString(), anyString(), anyList(), anyList()))
                .thenReturn(Map.of("subject", decision("subject", true), "secret", decision("secret", true)));
        var service = new FlowNodeFormService(conditions, policies, json);
        var nodes = List.of(node("START", "{\"moduleId\":51}", "{}"), node("APPROVAL", "{}", "{\"editable\":[\"subject\"]}"));
        assertFalse(service.inspect(actor, nodes).isEmpty());
        var snapshot = json.valueToTree(service.snapshot(actor, nodes));
        assertEquals(51, snapshot.path("moduleId").asLong());
        assertEquals(35, snapshot.path("moduleVersionId").asLong());
        assertEquals(177, snapshot.path("fields").get(0).path("id").asLong());
        assertFalse(snapshot.toString().contains("private_default"));
    }

    private RuntimeModuleConfiguration publication() throws Exception {
        var root = json.createObjectNode();
        root.putObject("module").put("code", "module_test");
        var fields = root.putArray("fields");
        for (String code : List.of("subject", "secret", "blocked")) {
            fields.addObject().put("id", code.equals("subject") ? 177 : 178).put("code", code)
                    .put("name", "测试字段").put("status", "ACTIVE").put("fieldType", code.equals("secret") ? "SECRET" : "TEXT")
                    .put("configJson", code.equals("subject") ? "{\"readOnly\":true,\"defaultValue\":\"private_default\"}" : "{}");
        }
        return new RuntimeModuleConfiguration(51L, 35L, 5, 0, root);
    }

    private FieldAccessDecision decision(String code, boolean readable) {
        return new FieldAccessDecision(code, "PAGE,FLOW", readable, readable, null, List.of(114L), "验收字段策略");
    }

    private FlowNode node(String type, String config, String policy) {
        var node = new FlowNode();
        node.setNodeKey(type.toLowerCase());
        node.setNodeType(type);
        node.setConfigJson(config);
        node.setFormPolicyJson(policy);
        return node;
    }
}
