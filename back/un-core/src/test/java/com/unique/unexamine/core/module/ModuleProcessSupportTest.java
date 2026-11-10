package com.unique.unexamine.core.module;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验证记录保存后的触发条件和发布快照解析规则。 */
class ModuleProcessSupportTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void matchesSafeStructuredConditions() {
        String conditions = "[{\"fieldKey\":\"amount\",\"operator\":\"GE\",\"value\":100},"
                + "{\"fieldKey\":\"status\",\"operator\":\"EQ\",\"value\":\"NEW\"}]";
        assertTrue(TriggerConditionMatcher.matches(json, conditions, Map.of("amount", 120, "status", "NEW")));
        assertFalse(TriggerConditionMatcher.matches(json, conditions, Map.of("amount", 99, "status", "NEW")));
    }

    @Test
    void rejectsInvalidConditionBeforePublishing() {
        assertThrows(IllegalArgumentException.class, () -> TriggerConditionMatcher.validate(json,
                "{\"fieldKey\":\"status\",\"operator\":\"GT\",\"value\":\"NEW\"}"));
        assertThrows(IllegalArgumentException.class, () -> TriggerConditionMatcher.validate(json,
                "{\"fieldKey\":\"status\",\"operator\":\"EQ\"}"));
    }

    @Test
    void rejectsConditionFieldOutsideCurrentModule() {
        String condition = "{\"fieldKey\":\"removed_field\",\"operator\":\"EQ\",\"value\":\"x\"}";
        assertThrows(IllegalArgumentException.class, () -> TriggerConditionMatcher.validateFieldKeys(
                json, condition, Set.of("status", "amount")));
        assertDoesNotThrow(() -> TriggerConditionMatcher.validateFieldKeys(json,
                "{\"field\":\"status\",\"operator\":\"EQ\",\"value\":\"NEW\"}",
                Set.of("status")));
        assertEquals(Set.of("amount", "status"), TriggerConditionMatcher.fieldKeys(json,
                "{\"conditions\":[{\"fieldKey\":\"amount\",\"value\":1},{\"field\":\"status\",\"value\":\"NEW\"}]}"));
    }

    @Test
    void choosesPublishedStartNodesWithoutReadingDrafts() {
        List<com.fasterxml.jackson.databind.JsonNode> nodes = PublishedProcessSnapshot.nodes(json,
                "{\"nodes\":[{\"nodeCode\":\"end\",\"nodeType\":\"END\"},"
                        + "{\"nodeCode\":\"start\",\"nodeType\":\"START\"}]}");
        assertEquals(List.of("start"), PublishedProcessSnapshot.startNodes(nodes).stream()
                .map(PublishedProcessSnapshot::code).toList());
        assertTrue(PublishedProcessSnapshot.startNodes(PublishedProcessSnapshot.nodes(json,
                "{\"nodes\":[{\"nodeCode\":\"approve\",\"nodeType\":\"APPROVAL\"},"
                        + "{\"nodeCode\":\"end\",\"nodeType\":\"END\"}]}"))
                .isEmpty());
    }

    @Test
    void rejectsInvalidFlowExamineReference() throws Exception {
        assertEquals(13L, PublishedProcessSnapshot.examineId(
                json.readTree("{\"examineId\":13}")));
        assertThrows(IllegalArgumentException.class, () -> PublishedProcessSnapshot.examineId(
                json.readTree("{\"examineId\":0}")));
    }

    @Test
    void readsOnlyCanonicalFlowEdges() {
        var edges = PublishedProcessSnapshot.edges(json,
                "{\"nodes\":[],\"edges\":[{\"sourceNodeCode\":\"start\",\"targetNodeCode\":\"next\"}]}");
        assertNotNull(edges);
        assertEquals("start", edges.get(0).path("sourceNodeCode").asText());
        assertEquals("next", edges.get(0).path("targetNodeCode").asText());
        assertNull(PublishedProcessSnapshot.edges(json,
                "{\"flow\":{\"nodes\":[],\"edges\":[]}}"));
    }

    @Test
    void parsesExternalCandidatesWithApplicationNamespace() throws Exception {
        var node = json.createObjectNode();
        node.set("approvers", json.readTree("[{\"type\":\"EXTERNAL\",\"applicationId\":7,\"externalId\":\"mail:a\"}]"));
        assertEquals(Set.of(new PublishedProcessSnapshot.ExternalCandidate(7L, "mail:a")),
                PublishedProcessSnapshot.externalCandidates(node));
        assertThrows(IllegalArgumentException.class, () -> PublishedProcessSnapshot.externalCandidates(
                json.readTree("{\"approvers\":[{\"type\":\"EXTERNAL\",\"externalId\":\"mail:a\"}]}")));
    }

    @Test
    void rejectsExternalCandidateIdLongerThanStorageContract() throws Exception {
        var node = json.createObjectNode();
        node.set("approvers", json.createArrayNode().add(json.createObjectNode()
                .put("type", "EXTERNAL").put("applicationId", 7)
                .put("externalId", "x".repeat(161))));
        assertThrows(IllegalArgumentException.class, () -> PublishedProcessSnapshot.externalCandidates(node));
    }

    @Test
    void rejectsInvalidAccountCandidateIds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PublishedProcessSnapshot.accountCandidates(
                json.readTree("{\"accountIds\":[0,-3]}")));
    }
}
