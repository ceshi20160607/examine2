package com.unique.examine.flow.manage.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.base.entity.FlowSnapshot;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审批运行态快照解析、节点推进和接收人解析工具。
 */
@Service
public class WorkflowRuntimeSupport {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String NODE_APPROVAL = "approval";
    private static final String NODE_CONDITION = "condition";
    private static final String NODE_END = "end";
    private static final String INSTANCE_APPROVED = "APPROVED";
    private static final String INSTANCE_REJECTED = "REJECTED";
    private static final String INSTANCE_TERMINATED = "TERMINATED";
    private static final int DEFAULT_DUE_MINUTES = 480;

    private final PlatMemberBaseService memberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final ObjectMapper objectMapper;

    public WorkflowRuntimeSupport(PlatMemberBaseService memberBaseService,
                                  PlatRoleBaseService roleBaseService,
                                  PlatRoleMemberBaseService roleMemberBaseService,
                                  PlatAccountMemberBindingBaseService bindingBaseService,
                                  ObjectMapper objectMapper) {
        this.memberBaseService = memberBaseService;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.bindingBaseService = bindingBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 找到发布快照中的第一个人工审批节点。
     *
     * @param snapshot 发布快照
     * @return 首个审批节点
     */
    public RuntimeNode firstApprovalNode(FlowSnapshot snapshot) {
        RuntimeGraph graph = graph(snapshot);
        return graph.nodes().values().stream()
                .filter(node -> NODE_APPROVAL.equals(node.nodeType()))
                .filter(RuntimeNode::runtimeExecutable)
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                        "流程快照没有可派发的人工审批节点"));
    }

    /**
     * 根据当前审批节点和审批输入，解析下一步人工节点或终态。
     *
     * @param snapshot 发布快照
     * @param currentNodeKey 当前节点编码
     * @param fieldValues 审批时提交的字段值
     * @return 下一步推进结果
     */
    public NextNodeResolution nextAfterApproval(FlowSnapshot snapshot, String currentNodeKey,
                                                Map<String, Object> fieldValues) {
        RuntimeGraph graph = graph(snapshot);
        Queue<String> queue = new ArrayDeque<>();
        selectedOutgoing(graph, currentNodeKey, fieldValues).forEach(edge -> queue.add(edge.targetNodeKey()));
        Set<String> visited = new LinkedHashSet<>();
        List<String> traversed = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeKey = queue.poll();
            if (!visited.add(nodeKey)) {
                continue;
            }
            RuntimeNode node = graph.nodes().get(nodeKey);
            if (Objects.isNull(node)) {
                continue;
            }
            traversed.add(node.nodeKey());
            if (NODE_END.equals(node.nodeType())) {
                return new NextNodeResolution(null, traversed, endStatus(node));
            }
            if (NODE_APPROVAL.equals(node.nodeType()) && node.runtimeExecutable()) {
                return new NextNodeResolution(node, traversed, null);
            }
            selectedOutgoing(graph, node.nodeKey(), fieldValues).forEach(edge -> queue.add(edge.targetNodeKey()));
        }
        return new NextNodeResolution(null, traversed, INSTANCE_APPROVED);
    }

    /**
     * 解析审批节点允许动作。
     *
     * @param node 审批节点
     * @return 动作编码列表
     */
    public List<String> allowedActions(RuntimeNode node) {
        List<String> actions = new ArrayList<>();
        actions.add("approve");
        if (bool(node.propertyPayload(), "allowReject", true)) {
            actions.add("reject");
        }
        if (bool(node.propertyPayload(), "allowTransfer", true)) {
            actions.add("transfer");
        }
        return actions;
    }

    /**
     * 计算审批节点到期时间。
     *
     * @param node 审批节点
     * @param now 当前时间
     * @return 到期时间
     */
    public LocalDateTime dueAt(RuntimeNode node, LocalDateTime now) {
        JsonNode timeoutPolicy = node.propertyPayload().get("timeoutPolicy");
        int minutes = integer(timeoutPolicy, "dueMinutes", DEFAULT_DUE_MINUTES);
        return now.plusMinutes(minutes <= 0 ? DEFAULT_DUE_MINUTES : minutes);
    }

    /**
     * 按审批节点配置解析接收系统成员。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param node 审批节点
     * @param fallbackMemberId 配置不可用时的兜底成员
     * @return 系统成员 ID
     */
    public Long resolveAssigneeMemberId(Long systemId, Long tenantId, RuntimeNode node, Long fallbackMemberId) {
        String assigneeType = text(node.propertyPayload(), "assigneeType", "MEMBER").toUpperCase();
        List<String> assigneeIds = stringList(node.propertyPayload().get("assigneeIds"));
        Long configuredAssignee = switch (assigneeType) {
            case "ROLE" -> resolveRoleAssignee(systemId, tenantId, assigneeIds);
            case "DEPARTMENT" -> resolveDepartmentAssignee(systemId, tenantId, assigneeIds);
            default -> resolveMemberAssignee(systemId, tenantId, assigneeIds);
        };
        if (Objects.nonNull(configuredAssignee)) {
            return configuredAssignee;
        }
        if (isActiveMember(systemId, tenantId, fallbackMemberId)) {
            return fallbackMemberId;
        }
        throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "审批节点没有可用处理人");
    }

    /**
     * 校验转交目标成员是否可接收审批。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param currentMemberId 当前处理人
     * @param targetMemberIdText 转交目标成员 ID
     * @return 目标成员 ID
     */
    public Long requireTransferTarget(Long systemId, Long tenantId, Long currentMemberId, String targetMemberIdText) {
        Long targetMemberId = parseLong(targetMemberIdText, "转交目标成员 ID 不合法");
        if (Objects.equals(currentMemberId, targetMemberId)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "审批任务不能转交给当前处理人");
        }
        if (!isActiveMember(systemId, tenantId, targetMemberId)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "转交目标成员不存在或不属于当前系统租户");
        }
        if (Objects.isNull(receiverAccountId(systemId, tenantId, targetMemberId))) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "转交目标成员没有可接收待办的账号绑定");
        }
        return targetMemberId;
    }

    /**
     * 查询系统成员对应的接收账号。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param systemMemberId 系统成员 ID
     * @return 账号 ID
     */
    public Long receiverAccountId(Long systemId, Long tenantId, Long systemMemberId) {
        if (Objects.isNull(systemMemberId)) {
            return null;
        }
        PlatAccountMemberBinding binding = bindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getSystemId, systemId)
                        .eq(PlatAccountMemberBinding::getTenantId, tenantId)
                        .eq(PlatAccountMemberBinding::getSystemMemberId, systemMemberId)
                        .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                        .last("LIMIT 1"), false);
        return Objects.isNull(binding) ? null : binding.getAccountId();
    }

    private RuntimeGraph graph(FlowSnapshot snapshot) {
        if (Objects.isNull(snapshot)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程发布快照不存在");
        }
        Map<String, RuntimeNode> nodes = new LinkedHashMap<>();
        for (JsonNode node : array(snapshot.getNodePayload(), "流程快照节点解析失败")) {
            RuntimeNode runtimeNode = node(node);
            nodes.put(runtimeNode.nodeKey(), runtimeNode);
        }
        List<RuntimeEdge> edges = new ArrayList<>();
        for (JsonNode edge : array(snapshot.getEdgePayload(), "流程快照连线解析失败")) {
            edges.add(edge(edge));
        }
        return new RuntimeGraph(nodes, edges);
    }

    private RuntimeNode node(JsonNode node) {
        String nodeType = text(node, "nodeType", NODE_APPROVAL);
        JsonNode propertyPayload = node.get("propertyPayload");
        return new RuntimeNode(text(node, "nodeKey", "node_" + nodeType),
                text(node, "nodeName", nodeType),
                nodeType,
                Objects.isNull(propertyPayload) || propertyPayload.isNull()
                        ? objectMapper.createObjectNode()
                        : propertyPayload,
                bool(node, "runtimeExecutable", true));
    }

    private RuntimeEdge edge(JsonNode edge) {
        return new RuntimeEdge(text(edge, "sourceNodeKey", null),
                text(edge, "targetNodeKey", null),
                edge.get("conditionPayload"));
    }

    private Iterable<JsonNode> array(String payload, String errorMessage) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isArray()) {
                return List.of();
            }
            List<JsonNode> values = new ArrayList<>();
            node.forEach(values::add);
            return values;
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, errorMessage);
        }
    }

    private List<RuntimeEdge> selectedOutgoing(RuntimeGraph graph, String sourceNodeKey,
                                               Map<String, Object> fieldValues) {
        List<RuntimeEdge> outgoing = graph.edges().stream()
                .filter(edge -> Objects.equals(edge.sourceNodeKey(), sourceNodeKey))
                .toList();
        if (outgoing.isEmpty()) {
            return List.of();
        }
        for (RuntimeEdge edge : outgoing) {
            if (matches(edge.conditionPayload(), fieldValues)) {
                return List.of(edge);
            }
        }
        return outgoing.stream()
                .filter(edge -> Objects.isNull(edge.conditionPayload()) || edge.conditionPayload().isNull())
                .findFirst()
                .map(List::of)
                .orElse(List.of(outgoing.get(0)));
    }

    private boolean matches(JsonNode conditionPayload, Map<String, Object> fieldValues) {
        if (Objects.isNull(conditionPayload) || conditionPayload.isNull()) {
            return true;
        }
        if (Objects.isNull(fieldValues) || fieldValues.isEmpty()) {
            return false;
        }
        String fieldCode = text(conditionPayload, "fieldCode", null);
        if (!StringUtils.hasText(fieldCode) || !fieldValues.containsKey(fieldCode)) {
            return false;
        }
        Object actual = fieldValues.get(fieldCode);
        JsonNode expectedNode = conditionPayload.get("expectedValue");
        String operator = text(conditionPayload, "operator", "EQ").toUpperCase();
        return switch (operator) {
            case "GTE" -> compare(actual, expectedNode) >= 0;
            case "GT" -> compare(actual, expectedNode) > 0;
            case "LTE" -> compare(actual, expectedNode) <= 0;
            case "LT" -> compare(actual, expectedNode) < 0;
            case "NE" -> !Objects.equals(stringValue(actual), jsonValue(expectedNode));
            case "CONTAINS" -> stringValue(actual).contains(jsonValue(expectedNode));
            default -> Objects.equals(stringValue(actual), jsonValue(expectedNode));
        };
    }

    private int compare(Object actual, JsonNode expectedNode) {
        Double actualNumber = number(actual);
        Double expectedNumber = number(expectedNode);
        if (Objects.nonNull(actualNumber) && Objects.nonNull(expectedNumber)) {
            return actualNumber.compareTo(expectedNumber);
        }
        return stringValue(actual).compareTo(jsonValue(expectedNode));
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof JsonNode node && node.isNumber()) {
            return node.doubleValue();
        }
        if (Objects.nonNull(value) && StringUtils.hasText(String.valueOf(value))) {
            try {
                return Double.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String endStatus(RuntimeNode node) {
        String status = text(node.propertyPayload(), "endStatus", INSTANCE_APPROVED).toUpperCase();
        if (INSTANCE_REJECTED.equals(status) || INSTANCE_TERMINATED.equals(status)) {
            return status;
        }
        return INSTANCE_APPROVED;
    }

    private Long resolveMemberAssignee(Long systemId, Long tenantId, List<String> assigneeIds) {
        for (String assigneeId : assigneeIds) {
            Long memberId = parseLongOrNull(assigneeId);
            if (isActiveMember(systemId, tenantId, memberId)) {
                return memberId;
            }
        }
        return null;
    }

    private Long resolveRoleAssignee(Long systemId, Long tenantId, List<String> assigneeIds) {
        List<PlatRole> roles = assigneeIds.isEmpty()
                ? List.of()
                : roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getScope, "system")
                .eq(PlatRole::getSystemId, systemId)
                .eq(PlatRole::getTenantId, tenantId)
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO)
                .and(wrapper -> {
                    for (String assigneeId : assigneeIds) {
                        Long parsedId = parseLongOrNull(assigneeId);
                        if (Objects.nonNull(parsedId)) {
                            wrapper.or().eq(PlatRole::getId, parsedId);
                        }
                        wrapper.or().eq(PlatRole::getRoleCode, assigneeId);
                    }
                })
                .orderByAsc(PlatRole::getId));
        for (PlatRole role : roles) {
            Long memberId = firstRoleMember(systemId, tenantId, role.getId());
            if (Objects.nonNull(memberId)) {
                return memberId;
            }
        }
        return null;
    }

    private Long firstRoleMember(Long systemId, Long tenantId, Long roleId) {
        List<PlatRoleMember> members = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getRoleId, roleId)
                .eq(PlatRoleMember::getSystemId, systemId)
                .eq(PlatRoleMember::getTenantId, tenantId)
                .isNotNull(PlatRoleMember::getSystemMemberId)
                .orderByAsc(PlatRoleMember::getId));
        for (PlatRoleMember member : members) {
            Long memberId = member.getSystemMemberId();
            if (isActiveMember(systemId, tenantId, memberId)
                    && Objects.nonNull(receiverAccountId(systemId, tenantId, memberId))) {
                return memberId;
            }
        }
        return null;
    }

    private Long resolveDepartmentAssignee(Long systemId, Long tenantId, List<String> assigneeIds) {
        for (String assigneeId : assigneeIds) {
            Long deptId = parseLongOrNull(assigneeId);
            if (Objects.isNull(deptId)) {
                continue;
            }
            List<PlatMember> members = memberBaseService.list(new LambdaQueryWrapper<PlatMember>()
                    .eq(PlatMember::getSystemId, systemId)
                    .eq(PlatMember::getTenantId, tenantId)
                    .eq(PlatMember::getDeptId, deptId)
                    .eq(PlatMember::getStatus, ENABLED)
                    .eq(PlatMember::getDeleted, DELETED_NO)
                    .orderByAsc(PlatMember::getId));
            for (PlatMember member : members) {
                if (Objects.nonNull(receiverAccountId(systemId, tenantId, member.getId()))) {
                    return member.getId();
                }
            }
        }
        return null;
    }

    private boolean isActiveMember(Long systemId, Long tenantId, Long memberId) {
        if (Objects.isNull(memberId)) {
            return false;
        }
        return memberBaseService.count(new LambdaQueryWrapper<PlatMember>()
                .eq(PlatMember::getId, memberId)
                .eq(PlatMember::getSystemId, systemId)
                .eq(PlatMember::getTenantId, tenantId)
                .eq(PlatMember::getStatus, ENABLED)
                .eq(PlatMember::getDeleted, DELETED_NO)) > 0;
    }

    private List<String> stringList(JsonNode node) {
        if (Objects.isNull(node) || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() || value.isNumber()) {
                values.add(value.asText());
            }
        });
        return values;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (Objects.isNull(node) || !node.isObject()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText()
                : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        if (Objects.isNull(node) || !node.isObject()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private int integer(JsonNode node, String field, int fallback) {
        if (Objects.isNull(node) || !node.isObject()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isInt() ? value.asInt() : fallback;
    }

    private Long parseLong(String value, String message) {
        Long parsed = parseLongOrNull(value);
        if (Objects.isNull(parsed)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
        return parsed;
    }

    private Long parseLongOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return Objects.isNull(value) ? "" : String.valueOf(value);
    }

    private String jsonValue(JsonNode value) {
        if (Objects.isNull(value) || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText() : value.asText("");
    }

    private record RuntimeGraph(Map<String, RuntimeNode> nodes, List<RuntimeEdge> edges) {
    }

    public record RuntimeNode(String nodeKey, String nodeName, String nodeType, JsonNode propertyPayload,
                              boolean runtimeExecutable) {
    }

    private record RuntimeEdge(String sourceNodeKey, String targetNodeKey, JsonNode conditionPayload) {
    }

    public record NextNodeResolution(RuntimeNode nextApprovalNode, List<String> traversedNodeKeys,
                                     String terminalStatus) {

        public boolean hasNextApprovalTask() {
            return Objects.nonNull(nextApprovalNode);
        }
    }
}
