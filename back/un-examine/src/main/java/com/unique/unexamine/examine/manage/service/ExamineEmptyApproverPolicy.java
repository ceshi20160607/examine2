package com.unique.unexamine.examine.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 审批节点无人可办时的配置校验与处理人解析；首节点和后续节点共用。 */
final class ExamineEmptyApproverPolicy {
    private static final Set<String> TYPES = Set.of("ADMIN", "ACCOUNT", "ROLE", "DEPARTMENT", "SUPERVISOR");

    private ExamineEmptyApproverPolicy() {}

    /** 审批管理员可在定义快照中明确指定；节点未配置异常规则时由管理员接办。 */
    static void validateDefinition(JsonNode snapshot) {
        JsonNode administrator = snapshot.isObject() ? snapshot.get("administratorAccountId") : null;
        if (administrator != null && (!administrator.canConvertToLong() || administrator.asLong() < 1))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批管理员账号无效");
    }

    /** 固定人、角色、部门和上级规则必须在保存审批定义时具备可解析的配置形态。 */
    static void validateNode(JsonNode node, String code) {
        validateNode(node, code, false);
    }

    /** 平台组织没有直系上级关系，平台审批的上级兜底须显式指定账号。 */
    static void validateNode(JsonNode node, String code, boolean platform) {
        JsonNode rule = rule(node);
        if (rule == null) return;
        if (!rule.isObject() || !TYPES.contains(type(rule)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点无人处理规则无效: " + code);
        String type = type(rule);
        if (Set.of("ACCOUNT", "ROLE", "DEPARTMENT").contains(type) && positiveId(rule) == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点无人处理目标无效: " + code);
        if ("SUPERVISOR".equals(type) && rule.has("accountId") && positiveId(rule) == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点上级账号无效: " + code);
        if (platform && "SUPERVISOR".equals(type) && positiveId(rule) == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台审批上级兜底须指定账号: " + code);
    }

    /** 使用实例保存的定义与管理员身份解析兜底人，不读取后来修改的审批配置。 */
    static Set<Long> resolve(ObjectMapper json, UnExamineExample example, JsonNode node,
                             Map<String, Object> values, List<ExamineCandidateResolver> resolvers) {
        JsonNode runtime;
        try { runtime = json.readTree(example.getRuntimeSnapshotJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照无法读取", ex); }
        JsonNode rule = rule(node);
        String type = rule == null ? "ADMIN" : type(rule);
        ObjectNode candidate = json.createObjectNode();
        ArrayNode approvers = candidate.putArray("approvers");
        ObjectNode approver = approvers.addObject();
        if ("ADMIN".equals(type)) {
            JsonNode configured = runtime.path("definition").path("administratorAccountId");
            long administrator = configured.canConvertToLong() ? configured.asLong()
                    : runtime.path("administratorAccountId").asLong(0);
            if (administrator < 1) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批管理员未配置");
            approver.put("type", "ACCOUNT").put("accountId", administrator);
        } else {
            approver.put("type", type);
            Long id = positiveId(rule);
            if (id != null) {
                if ("SUPERVISOR".equals(type)) approver.put("supervisorAccountId", id);
                else approver.put("subjectId", id);
            }
        }
        ExamineCandidateContext context = new ExamineCandidateContext(example.getSystemId(), example.getTenantId(),
                example.getModuleId(), example.getRecordId(), example.getInitiatorAccountId(), values);
        Set<Long> result = new LinkedHashSet<>();
        for (ExamineCandidateResolver resolver : resolvers) {
            Set<Long> resolved = resolver.resolve(candidate, context);
            if (resolved != null) result.addAll(resolved);
        }
        return result;
    }

    /** 日志保留命中的异常规则类型，便于解释处理人为何与原节点配置不同。 */
    static String ruleType(JsonNode node) {
        JsonNode rule = rule(node);
        return rule == null ? "ADMIN" : type(rule);
    }

    private static JsonNode rule(JsonNode node) {
        JsonNode direct = node.get("emptyApprover");
        return direct != null ? direct : node.path("config").get("emptyApprover");
    }

    private static String type(JsonNode rule) {
        return rule.path("type").asText("").trim().toUpperCase(Locale.ROOT);
    }

    private static Long positiveId(JsonNode rule) {
        for (String key : List.of("subjectId", "accountId", "roleId", "departmentId", "supervisorAccountId")) {
            JsonNode value = rule.get(key);
            if (value != null && value.canConvertToLong() && value.asLong() > 0) return value.asLong();
        }
        return null;
    }
}
