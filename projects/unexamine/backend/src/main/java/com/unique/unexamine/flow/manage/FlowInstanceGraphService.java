package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

/** 实例图由不可变发布快照及本实例的持久变更组成，执行、详情与业务进度使用同一张图。 */
@Service
public class FlowInstanceGraphService {
    private final FlowActionBaseService actions;
    private final ObjectMapper mapper;

    public FlowInstanceGraphService(FlowActionBaseService actions, ObjectMapper mapper) {
        this.actions = actions;
        this.mapper = FlowJsonStorage.mapper(mapper);
    }

    /** 修订号只随图变更增长，普通审批造成的实例修订与它分别检查。 */
    public record Graph(Map<String, Object> snapshot, long revision) { }

    public Graph load(FlowInstance instance, Map<String, Object> frozen) {
        Map<String, Object> graph = copy(frozen);
        long revision = 0;
        for (FlowAction action : actions.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId())
                .eq(FlowAction::getActionCode, "RUNTIME_NODE_ADDED").orderByAsc(FlowAction::getId))) {
            Map<String, Object> change = read(action.getInputJson());
            if (!Objects.equals(String.valueOf(instance.getId()), String.valueOf(change.get("instanceId")))
                    || !Objects.equals(String.valueOf(instance.getFlowVersionId()), String.valueOf(change.get("flowVersionId")))
                    || !Objects.equals(String.valueOf(revision), String.valueOf(change.get("previousGraphRevision")))
                    || !List.of("AFTER_CURRENT", "BEFORE_CURRENT", "BEFORE_NODE", "AFTER_NODE").contains(change.get("position"))
                    || !Objects.equals(action.getNodeKey(), object(change.get("node")).get("nodeKey"))
                    || action.getActedByAccountId() == null) {
                throw invalid();
            }
            apply(graph, change);
            revision = action.getId();
        }
        return new Graph(graph, revision);
    }

    /** 插入只改尚未从当前节点出发的连线，原连线条件和优先级移到新增节点后。 */
    public Map<String, Object> afterCurrent(FlowInstance instance, Graph graph, Map<String, Object> node) {
        return insert(instance, graph, node, "AFTER_CURRENT", instance.getCurrentNodeKey());
    }

    /** 前插保留各入边的条件与优先级，后插保留各出边；不把多分支改成无条件到达。 */
    public Map<String, Object> insert(FlowInstance instance, Graph graph, Map<String, Object> node,
            String position, String anchor) {
        boolean before = List.of("BEFORE_CURRENT", "BEFORE_NODE").contains(position);
        if (!List.of("AFTER_CURRENT", "BEFORE_CURRENT", "BEFORE_NODE", "AFTER_NODE").contains(position)) throw invalid();
        List<Map<String, Object>> affected = maps(graph.snapshot().get("edges")).stream()
                .filter(edge -> Objects.equals(anchor, edge.get(before ? "targetNodeKey" : "sourceNodeKey"))).toList();
        if (affected.isEmpty()) {
            throw new DomainException("FLOW_RUNTIME_NODE_PATH_INVALID", "指定节点没有可插入的路径", HttpStatus.CONFLICT);
        }
        List<Map<String, Object>> replacement = new ArrayList<>();
        replacement.add(new LinkedHashMap<>(Map.of("edgeKey", node.get("nodeKey") + "_entry",
                "sourceNodeKey", before ? node.get("nodeKey") : anchor,
                "targetNodeKey", before ? anchor : node.get("nodeKey"), "config", Map.of(),
                "priorityOrder", 0, "conditionExpression", "")));
        for (Map<String, Object> edge : affected) {
            Map<String, Object> shifted = copy(edge);
            shifted.put(before ? "targetNodeKey" : "sourceNodeKey", node.get("nodeKey"));
            replacement.add(shifted);
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("instanceId", instance.getId());
        change.put("flowVersionId", instance.getFlowVersionId());
        change.put("previousGraphRevision", graph.revision());
        change.put("position", position);
        change.put("anchorNodeKey", anchor);
        change.put("node", copy(node));
        change.put("removedEdges", affected);
        change.put("replacementEdges", replacement);
        // 写入前使用与重启读回相同的校验，不能保存无法重新加载的变更。
        apply(copy(graph.snapshot()), change);
        return change;
    }

    /** 仅从当前节点沿未进入路径查找未来锚点；跳过历史节点，不能借循环重新打开已处理路径。 */
    public List<FlowRuntimeModels.RuntimeNodeAnchor> anchors(Map<String, Object> graph, String current,
            Set<String> entered) {
        return anchors(graph, current, entered, Set.of());
    }

    /** 前插期间允许穿过可信暂停锚点查找未来路径，但暂停及已进入节点始终不能被选择。 */
    public List<FlowRuntimeModels.RuntimeNodeAnchor> anchors(Map<String, Object> graph, String current,
            Set<String> entered, Set<String> suspendedTransit) {
        List<Map<String, Object>> edges = maps(graph.get("edges"));
        Set<String> reached = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(current);
        for (int index = 0; index < queue.size(); index++) {
            String source = queue.get(index);
            for (Map<String, Object> edge : edges) {
                if (!Objects.equals(source, edge.get("sourceNodeKey"))) continue;
                String target = String.valueOf(edge.get("targetNodeKey"));
                if (Objects.equals(current, target) || (entered.contains(target) && !suspendedTransit.contains(target))
                        || !reached.add(target)) continue;
                queue.add(target);
            }
        }
        return maps(graph.get("nodes")).stream().filter(node -> reached.contains(node.get("nodeKey")))
                .filter(node -> !entered.contains(node.get("nodeKey")))
                .filter(node -> !"START".equals(node.get("nodeType")))
                .map(node -> new FlowRuntimeModels.RuntimeNodeAnchor(String.valueOf(node.get("nodeKey")),
                        String.valueOf(node.get("name")), String.valueOf(node.get("nodeType")),
                        edges.stream().anyMatch(edge -> Objects.equals(node.get("nodeKey"), edge.get("targetNodeKey"))),
                        !"END".equals(node.get("nodeType")) && edges.stream()
                                .anyMatch(edge -> Objects.equals(node.get("nodeKey"), edge.get("sourceNodeKey")))))
                .toList();
    }

    private void apply(Map<String, Object> graph, Map<String, Object> change) {
        List<Map<String, Object>> nodes = new ArrayList<>(maps(graph.get("nodes")));
        List<Map<String, Object>> edges = new ArrayList<>(maps(graph.get("edges")));
        Map<String, Object> node = object(change.get("node"));
        Object key = node.get("nodeKey"), anchor = change.get("anchorNodeKey");
        boolean before = List.of("BEFORE_CURRENT", "BEFORE_NODE").contains(change.get("position"));
        List<Map<String, Object>> affected = edges.stream()
                .filter(edge -> Objects.equals(anchor, edge.get(before ? "targetNodeKey" : "sourceNodeKey"))).toList();
        if (!(key instanceof String text) || !text.startsWith("runtime_") || nodes.size() >= 200
                || !List.of("APPROVAL", "NOTIFICATION").contains(node.get("nodeType"))
                || nodes.stream().anyMatch(existing -> Objects.equals(key, existing.get("nodeKey")))
                || nodes.stream().noneMatch(existing -> Objects.equals(anchor, existing.get("nodeKey")))
                || affected.isEmpty() || !affected.equals(maps(change.get("removedEdges")))) throw invalid();
        List<Map<String, Object>> replacement = maps(change.get("replacementEdges"));
        if (replacement.size() != affected.size() + 1) throw invalid();
        Map<String, Object> bridge = replacement.getFirst();
        if (!Objects.equals(before ? key : anchor, bridge.get("sourceNodeKey"))
                || !Objects.equals(before ? anchor : key, bridge.get("targetNodeKey"))
                || !object(bridge.get("config")).isEmpty()
                || !"".equals(bridge.get("conditionExpression"))) throw invalid();
        for (int i = 0; i < affected.size(); i++) {
            Map<String, Object> expected = copy(affected.get(i));
            expected.put(before ? "targetNodeKey" : "sourceNodeKey", key);
            if (!expected.equals(replacement.get(i + 1))) throw invalid();
        }
        edges.removeAll(affected);
        nodes.add(copy(node));
        edges.addAll(replacement);
        if (edges.stream().map(edge -> edge.get("edgeKey")).distinct().count() != edges.size()) throw invalid();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return read(write(source));
    }

    private String write(Map<String, Object> value) {
        try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw invalid(); }
    }

    private Map<String, Object> read(String json) {
        try { return mapper.readValue(json, new TypeReference<>() { }); } catch (Exception exception) { throw invalid(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map))) throw invalid();
        return list.stream().map(this::object).toList();
    }

    private DomainException invalid() {
        return new DomainException("FLOW_INSTANCE_GRAPH_INVALID", "实例流程图变更不完整，请联系管理员处理", HttpStatus.CONFLICT);
    }
}
