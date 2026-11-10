package com.unique.unexamine.core.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 发布版 Flow/审批快照的最小公共解析规则。 */
public final class PublishedProcessSnapshot {
    private PublishedProcessSnapshot() {}

    /** 读取快照中的节点数组；不回退读取草稿表，保证运行实例使用发布时内容。 */
    public static List<JsonNode> nodes(ObjectMapper mapper, String snapshotJson) {
        try {
            JsonNode root = mapper.readTree(snapshotJson);
            JsonNode nodes = root == null ? null : root.get("nodes");
            if (nodes == null || !nodes.isArray()) throw new IllegalArgumentException("发布快照缺少 nodes 数组");
            List<JsonNode> result = new ArrayList<>();
            nodes.forEach(result::add);
            result.sort(Comparator.comparingInt(node -> node.path("sortNo").asInt(0)));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("发布快照不是有效 JSON", ex);
        }
    }

    /**
     * 读取发布快照顶层的线路数组。
     * 运行与发布校验必须使用同一解析规则，避免首节点和后续节点走不同线路。
     */
    public static JsonNode edges(ObjectMapper mapper, String snapshotJson) {
        try {
            JsonNode root = mapper.readTree(snapshotJson);
            if (root == null) return null;
            JsonNode edges = root.isObject() ? root.get("edges") : null;
            return edges;
        } catch (Exception ex) {
            throw new IllegalArgumentException("发布快照不是有效 JSON", ex);
        }
    }

    /** 只返回明确声明的开始节点；发布校验和运行实例共用此规则。 */
    public static List<JsonNode> startNodes(List<JsonNode> nodes) {
        return nodes.stream().filter(PublishedProcessSnapshot::isStart).toList();
    }

    public static String code(JsonNode node) {
        String code = node.path("nodeCode").asText("");
        if (code.isBlank()) throw new IllegalArgumentException("发布节点缺少 nodeCode");
        return code;
    }

    public static String type(JsonNode node) {
        return node.path("nodeType").asText("");
    }

    /** Flow 节点引用的审批定义；配置了引用时必须是有效正整数。 */
    public static Long examineId(JsonNode node) {
        JsonNode value = node.get("examineId");
        if (value == null) value = node.path("config").get("examineId");
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToLong() || value.asLong() < 1)
            throw new IllegalArgumentException("Flow 节点的 examineId 必须是正整数");
        return value.asLong();
    }

    /** 从节点配置中读取明确声明的内部账号候选人。没有候选人时由后续处理器负责动态解析。 */
    public static Set<Long> accountCandidates(JsonNode node) {
        Set<Long> result = new LinkedHashSet<>();
        addAccounts(result, node.get("accountIds"));
        addAccounts(result, node.get("candidateAccountIds"));
        JsonNode config = node.get("config");
        if (config != null) {
            addAccounts(result, config.get("accountIds"));
            addAccounts(result, config.get("candidateAccountIds"));
        }
        return result;
    }

    /** 发布快照中的外部审批候选人；应用主键和外部标识共同构成身份命名空间。 */
    public static Set<ExternalCandidate> externalCandidates(JsonNode node) {
        Set<ExternalCandidate> result = new LinkedHashSet<>();
        addExternal(result, node, node.get("externalCandidates"));
        addExternal(result, node, node.get("externalApprovers"));
        addExternal(result, node, node.get("approvers"));
        JsonNode config = node.get("config");
        if (config != null) {
            addExternal(result, node, config.get("externalCandidates"));
            addExternal(result, node, config.get("externalApprovers"));
            addExternal(result, node, config.get("approvers"));
        }
        return result;
    }

    private static void addExternal(Set<ExternalCandidate> result, JsonNode node, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(item -> {
            String type = item.path("type").asText(item.path("subjectType").asText(""))
                    .toUpperCase(Locale.ROOT);
            boolean external = Set.of("EXTERNAL", "EXTERNAL_USER", "EMAIL", "APP").contains(type)
                    || item.has("externalId");
            if (!external) return;
            JsonNode app = item.get("applicationId");
            if (app == null) app = item.get("application_id");
            if (app == null) app = node.get("applicationId");
            if (app == null && node.get("config") != null) app = node.get("config").get("applicationId");
            String externalId = item.path("externalId").asText(item.path("external_id").asText(""));
            if (app == null || !app.canConvertToLong() || app.asLong() < 1
                    || externalId.isBlank() || externalId.length() > 160)
                throw new IllegalArgumentException("外部审批候选人必须配置有效 applicationId 和 160 字符以内的 externalId");
            result.add(new ExternalCandidate(app.asLong(), externalId));
        });
    }

    /** 外部审批身份；不使用平台账号主键，避免跨应用的外部标识碰撞。 */
    public record ExternalCandidate(Long applicationId, String externalId) {}

    private static void addAccounts(Set<Long> result, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(value -> {
            if (!value.canConvertToLong() || value.asLong() < 1)
                throw new IllegalArgumentException("Flow 或审批候选账号必须是正整数");
            result.add(value.asLong());
        });
    }

    private static boolean isStart(JsonNode node) {
        String type = type(node).toUpperCase(Locale.ROOT);
        return type.equals("START") || type.equals("BEGIN") || type.equals("START_EVENT");
    }

}
