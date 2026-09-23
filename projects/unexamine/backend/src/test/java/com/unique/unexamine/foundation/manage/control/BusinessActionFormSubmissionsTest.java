package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.shared.manage.security.RuntimeSecretCipher;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 独立节点快照的密钥保护、绑定防搬移、空值语义和原身份保持；不启动数据库或新容器。 */
class BusinessActionFormSubmissionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FlowActionBaseService actions = mock(FlowActionBaseService.class);
    private final FlowTaskBaseService tasks = mock(FlowTaskBaseService.class);
    private final BusinessActionFormSubmissions submissions = new BusinessActionFormSubmissions(actions, tasks, json,
            new BusinessActionValueProtection(json, new RuntimeSecretCipher("isolated-node-form-synthetic-test-key-only")));

    private BusinessAction.Input original(String actionCode) throws Exception {
        var actor = new AuthenticatedContext(null, 69L, 1L, 54L, 57L, 101L, 108L,
                "node-test", "测试发起人", "NONE", List.of(), List.of(), Map.of());
        return new BusinessAction.Input(actor, 77L, 45L, "test_module", 9L, actionCode, 151L, 11,
                json.readTree("{\"fields\":{\"subject\":\"原提交\",\"untouched\":false},\"values\":{\"subject\":\"原动作参数\"},\"version\":11}"),
                BusinessAction.Options.page("original-request"), "original-trace");
    }

    private FlowInstance flow() {
        FlowInstance flow = new FlowInstance(); flow.setId(90L); flow.setFlowVersionId(37L); return flow;
    }

    private FlowTask task(long id) {
        FlowTask task = new FlowTask(); task.setId(id); task.setInstanceId(90L); task.setNodeKey("review");
        task.setAssigneeAccountId(70L); task.setStatus("APPROVE"); when(tasks.selectById(id)).thenReturn(task); return task;
    }

    private FlowAction action(long id, FlowTask task, ObjectNode snapshot) throws Exception {
        FlowAction action = new FlowAction(); action.setId(id); action.setInstanceId(90L); action.setTaskId(task.getId());
        action.setNodeKey("review"); action.setActionCode("APPROVE"); action.setActedByAccountId(70L);
        ObjectNode input = json.createObjectNode(); input.set("formSubmission", snapshot);
        action.setInputJson(json.writeValueAsString(input)); return action;
    }

    @Test
    void protectsSecretAndExecutionPreservesOriginalIdentityAndRevision() throws Exception {
        var original = original("UPDATE"); var task = task(1L);
        var snapshot = submissions.snapshot(original, json.readTree("{\"fields\":[{\"code\":\"password\",\"fieldType\":\"SECRET\"}]}"),
                flow(), task, Map.of("password", json.valueToTree("synthetic-new-secret"), "subject", json.valueToTree("批准修改")));
        assertFalse(snapshot.toString().contains("synthetic-new-secret"));
        when(actions.selectList(any())).thenReturn(List.of(action(1L, task, snapshot)));
        var merged = submissions.mergeForExecution(original, flow());
        JsonNode payload = json.valueToTree(merged.payload());
        assertEquals("synthetic-new-secret", payload.path("fields").path("password").asText());
        assertFalse(payload.path("fields").path("untouched").asBoolean());
        assertEquals(original.actor(), merged.actor()); assertEquals(77L, merged.dataTenantId());
        assertEquals(11, merged.expectedVersion()); assertEquals(9L, merged.configVersionId());
        assertEquals(original.options(), merged.options()); assertEquals("original-trace", merged.traceId());
        var read = submissions.submittedForRead(original, flow());
        assertEquals("***", json.valueToTree(read.payload()).path("fields").path("password").asText());
        assertFalse(json.valueToTree(read.payload()).path("fields").has("untouched"));
        assertEquals("原提交", json.valueToTree(original.payload()).path("fields").path("subject").asText());
    }

    @Test
    void createDraftKeepsNullRecordAndMergesProtectedFieldsWithoutCustomValues() throws Exception {
        var base = original("CREATE");
        var original = new BusinessAction.Input(base.actor(), 57L, base.moduleId(), base.moduleCode(),
                base.configVersionId(), "CREATE", null, null, base.payload(), base.options(), base.traceId());
        var task = task(7L);
        var snapshot = submissions.snapshot(original,
                json.readTree("{\"fields\":[{\"code\":\"password\",\"fieldType\":\"SECRET\"}]}"), flow(), task,
                Map.of("subject", json.valueToTree("新建节点补充"), "password", json.valueToTree("synthetic-create-secret")));
        assertFalse(snapshot.toString().contains("synthetic-create-secret"));
        when(actions.selectList(any())).thenReturn(List.of(action(7L, task, snapshot)));
        var merged = submissions.mergeForExecution(original, flow());
        var payload = json.valueToTree(merged.payload());
        assertNull(merged.recordId()); assertNull(merged.expectedVersion());
        assertEquals("新建节点补充", payload.path("fields").path("subject").asText());
        assertEquals("synthetic-create-secret", payload.path("fields").path("password").asText());
        assertFalse(payload.path("fields").path("untouched").asBoolean());
        assertEquals("原动作参数", payload.path("values").path("subject").asText());
        assertEquals(original.actor(), merged.actor()); assertEquals(original.options(), merged.options());
        assertEquals("***", json.valueToTree(submissions.submittedForRead(original, flow()).payload())
                .path("fields").path("password").asText());
    }

    @Test
    void explicitNullClearsWhileMissingRetainsAndCustomUsesValues() throws Exception {
        var original = original("ACTION_FILL"); var task = task(2L);
        var snapshot = submissions.snapshot(original, json.createObjectNode(), flow(), task,
                Map.of("subject", json.nullNode()));
        when(actions.selectList(any())).thenReturn(List.of(action(2L, task, snapshot)));
        var payload = json.valueToTree(submissions.mergeForExecution(original, flow()).payload());
        assertTrue(payload.path("values").path("subject").isNull());
        assertEquals("原提交", payload.path("fields").path("subject").asText());
    }

    @Test
    void refusesSnapshotMovedToDifferentTask() throws Exception {
        var original = original("UPDATE"); var first = task(3L); var other = task(4L);
        var snapshot = submissions.snapshot(original, json.createObjectNode(), flow(), first, Map.of("subject", json.valueToTree("不应写入")));
        when(actions.selectList(any())).thenReturn(List.of(action(4L, other, snapshot)));
        assertEquals("ACTION_NODE_FORM_INVALID", assertThrows(DomainException.class,
                () -> submissions.mergeForExecution(original, flow())).code());
    }

    @Test
    void refusesNotApprovedTaskAndWrongActor() throws Exception {
        var original = original("UPDATE"); var task = task(5L);
        var snapshot = submissions.snapshot(original, json.createObjectNode(), flow(), task, Map.of("subject", json.valueToTree("不应写入")));
        var action = action(5L, task, snapshot); when(actions.selectList(any())).thenReturn(List.of(action));
        task.setStatus("REJECT");
        assertThrows(DomainException.class, () -> submissions.mergeForExecution(original, flow()));
        task.setStatus("APPROVE"); action.setActedByAccountId(71L);
        assertThrows(DomainException.class, () -> submissions.mergeForExecution(original, flow()));
    }

    @Test
    void emptyHistoryPreservesOriginalAndLegacyApprovalHasNoPatch() throws Exception {
        var original = original("UPDATE"); var task = task(6L); var action = action(6L, task, json.createObjectNode());
        action.setInputJson("{\"variables\":{}}" ); when(actions.selectList(any())).thenReturn(List.of(action));
        assertSame(original, submissions.mergeForExecution(original, flow()));
        assertNull(submissions.submittedForRead(original, flow()));
    }

    @Test
    void systemOrSignAcceptsFrozenSourceMemberButRefusesDifferentMemberOrExplicitWrongAccount() throws Exception {
        var original = original("CREATE"); var task = task(8L);
        task.setAssigneeAccountId(null);
        task.setAssigneeSnapshotJson("{\"resolvedPeople\":[{\"tenantMemberId\":108}]}" );
        var snapshot = submissions.snapshot(original, json.createObjectNode(), flow(), task,
                Map.of("subject", json.valueToTree("或签批准补充")));
        var action = action(8L, task, snapshot); action.setActedByTenantMemberId(108L);
        when(actions.selectList(any())).thenReturn(List.of(action));
        assertEquals("或签批准补充", json.valueToTree(submissions.mergeForExecution(original, flow()).payload()).path("fields").path("subject").asText());
        action.setActedByTenantMemberId(109L);
        assertThrows(DomainException.class, () -> submissions.mergeForExecution(original, flow()));
        action.setActedByTenantMemberId(108L);
        task.setAssigneeSnapshotJson("{\"resolvedPeople\":[{\"tenantMemberId\":108,\"accountId\":71}]}" );
        assertThrows(DomainException.class, () -> submissions.mergeForExecution(original, flow()));
    }

    @Test
    void resubmittedCorrectionBecomesLatestCandidateWithoutChangingOriginalIntent() throws Exception {
        var original = original("UPDATE"); var task = task(9L);
        var snapshot = submissions.snapshot(original, json.createObjectNode(), flow(), task,
                Map.of("subject", json.valueToTree("退回后修正")));
        var action = action(9L, task, snapshot);
        task.setStatus("RESUBMIT"); action.setActionCode("RESUBMIT");
        when(actions.selectList(any())).thenReturn(List.of(action));
        var merged = submissions.mergeForExecution(original, flow());
        assertEquals("退回后修正", json.valueToTree(merged.payload()).path("fields").path("subject").asText());
        assertEquals(original.actor(), merged.actor());
        assertEquals(original.expectedVersion(), merged.expectedVersion());
    }
}
