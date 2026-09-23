package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 针对可信图读回的篡改隔离和有历史循环的未来位置，不复制运行时业务测试。 */
class FlowInstanceGraphTest {
    @Test
    void persistedChangeMustBelongToItsInstanceAndMatchOriginalEdges() throws Exception {
        FlowActionBaseService actions = mock(FlowActionBaseService.class);
        ObjectMapper mapper = new ObjectMapper();
        FlowInstanceGraphService graphs = new FlowInstanceGraphService(actions, mapper);
        FlowInstance instance = new FlowInstance();
        instance.setId(8L); instance.setFlowVersionId(11L); instance.setCurrentNodeKey("review");
        Map<String, Object> frozen = graph();
        Map<String, Object> change = graphs.insert(instance, new FlowInstanceGraphService.Graph(frozen, 0),
                Map.of("nodeKey", "runtime_extra", "nodeType", "APPROVAL"), "BEFORE_NODE", "future");
        FlowAction action = new FlowAction();
        action.setId(21L); action.setNodeKey("runtime_extra"); action.setActedByAccountId(5L);
        action.setInputJson(mapper.writeValueAsString(change));
        when(actions.selectList(any())).thenReturn(List.of(action));
        assertThat(graphs.load(instance, frozen).revision()).isEqualTo(21);
        // 记录即使能解析，也不能跨实例复用，或用不同的原边偷偷改变分支。
        change.put("instanceId", 9L); action.setInputJson(mapper.writeValueAsString(change));
        assertThatThrownBy(() -> graphs.load(instance, frozen)).isInstanceOf(DomainException.class);
        change.put("instanceId", 8L); change.put("removedEdges", List.of());
        action.setInputJson(mapper.writeValueAsString(change));
        assertThatThrownBy(() -> graphs.load(instance, frozen)).isInstanceOf(DomainException.class);
    }

    @Test
    void historicalCycleCannotExposeEnteredNodesButFutureDynamicNodeRemainsSelectable() {
        FlowInstanceGraphService graphs = new FlowInstanceGraphService(mock(FlowActionBaseService.class), new ObjectMapper());
        var anchors = graphs.anchors(graph(), "review", Set.of("start", "past", "review"));
        assertThat(anchors).extracting(FlowRuntimeModels.RuntimeNodeAnchor::nodeKey)
                .containsExactly("future", "runtime_later", "end");
        assertThat(anchors.getLast().beforeAllowed()).isTrue();
        assertThat(anchors.getLast().afterAllowed()).isFalse();
        // runtime_later仅有图修改历史时未进入；有任何排队/暂停任务或执行后则排除。
        assertThat(graphs.anchors(graph(), "review", Set.of("start", "past", "review", "runtime_later")))
                .extracting(FlowRuntimeModels.RuntimeNodeAnchor::nodeKey).containsExactly("future");
    }

    @Test
    void beforeCurrentFoldTraversesSuspendedOriginalWithoutOfferingHistoricalAnchors() throws Exception {
        FlowActionBaseService actions = mock(FlowActionBaseService.class);
        ObjectMapper mapper = new ObjectMapper();
        FlowInstanceGraphService graphs = new FlowInstanceGraphService(actions, mapper);
        FlowInstance instance = new FlowInstance();
        instance.setId(8L); instance.setFlowVersionId(11L); instance.setCurrentNodeKey("review");
        Map<String, Object> change = graphs.insert(instance, new FlowInstanceGraphService.Graph(graph(), 0),
                Map.of("nodeKey", "runtime_before", "nodeType", "APPROVAL", "name", "前插审批"), "BEFORE_CURRENT", "review");
        FlowAction action = new FlowAction();
        action.setId(22L); action.setNodeKey("runtime_before"); action.setActedByAccountId(5L);
        action.setInputJson(mapper.writeValueAsString(change));
        when(actions.selectList(any())).thenReturn(List.of(action));
        Map<String, Object> folded = graphs.load(instance, graph()).snapshot();
        Set<String> entered = Set.of("start", "past", "review", "runtime_before");
        assertThat(graphs.anchors(folded, "runtime_before", entered)).isEmpty();
        // 只有持久暂停协议提供的原节点可以中转，历史循环节点仍不能选或重开。
        assertThat(graphs.anchors(folded, "runtime_before", entered, Set.of("review")))
                .extracting(FlowRuntimeModels.RuntimeNodeAnchor::nodeKey).containsExactly("future", "runtime_later", "end");
    }

    private Map<String, Object> graph() {
        return Map.of("nodes", List.of(node("start", "START"), node("past", "APPROVAL"),
                node("review", "APPROVAL"), node("future", "APPROVAL"),
                node("runtime_later", "NOTIFICATION"), node("end", "END")),
                "edges", List.of(edge("a", "start", "past"), edge("b", "past", "review"),
                        edge("c", "review", "future"), edge("d", "future", "past"),
                        edge("e", "future", "runtime_later"), edge("f", "runtime_later", "end")));
    }

    private Map<String, Object> node(String key, String type) {
        return Map.of("nodeKey", key, "nodeType", type, "name", key);
    }

    private Map<String, Object> edge(String key, String source, String target) {
        return Map.of("edgeKey", key, "sourceNodeKey", source, "targetNodeKey", target,
                "priorityOrder", 0, "conditionExpression", "", "config", Map.of());
    }
}
