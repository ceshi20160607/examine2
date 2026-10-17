package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 用当前系统授权模型解释公开错误码；解释不可用时原业务错误保持原样。 */
@Service
public class AiErrorExplanationService {
    private static final Logger LOG = LoggerFactory.getLogger(AiErrorExplanationService.class);
    private static final Set<String> SAFE_OPERATIONS = Set.of(
            "新建", "编辑", "保存", "删除", "查看", "查询", "搜索", "提交", "发布", "审批", "导入", "导出",
            "保存模块草稿", "发布模块", "保存字段", "保存页面", "保存流程草稿", "发起流程", "处理待办",
            "新建业务记录", "更新业务记录");
    private static final Map<String, String> PUBLIC_MESSAGES = Map.ofEntries(
            Map.entry("VALIDATION_FAILED", "请求内容不符合要求"),
            Map.entry("REQUEST_BODY_INVALID", "请求内容格式不正确"),
            Map.entry("RESOURCE_CONFLICT", "唯一编码已被使用，或关联数据不属于当前范围"),
            Map.entry("RECORD_WRITE_CONFLICT", "记录正在被其他操作处理，请刷新后重试"),
            Map.entry("DRAFT_VERSION_CONFLICT", "草稿已被其他操作修改"),
            Map.entry("FIELD_VALIDATION_FAILED", "业务字段填写不符合当前模块规则"),
            Map.entry("UNIQUE_RULE_VIOLATION", "唯一性规则要求填写未被使用的值"),
            Map.entry("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改"),
            Map.entry("PERMISSION_DENIED", "当前成员没有执行此操作的权限"),
            Map.entry("CONFIGURATION_NOT_FOUND", "当前范围内找不到配置对象"),
            Map.entry("FORM_LAYOUT_INVALID", "表单布局配置不符合要求"),
            Map.entry("INTERNAL_ERROR", "服务处理失败，需要按请求编号排查"));

    private final AiSystemModelGrantBaseService grants;
    private final AiModelBaseService models;
    private final PermissionChecker permissions;
    private final AiModelGateway gateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiErrorExplanationService(AiSystemModelGrantBaseService grants,
            AiModelBaseService models, PermissionChecker permissions, AiModelGateway gateway,
            AuditRecorder audit, ObjectMapper json) {
        this.grants = grants;
        this.models = models;
        this.permissions = permissions;
        this.gateway = gateway;
        this.audit = audit;
        this.json = json;
    }

    /** 仅将固定公开语义送给模型；客户端消息和请求编号留在当前响应中，不进入提示词。 */
    public AiErrorExplanationModels.Explanation explain(AuthenticatedContext context,
            AiErrorExplanationModels.ExplainRequest input, String traceId) {
        String code = input.errorCode().strip();
        String originalMessage = input.errorMessage() == null ? "" : input.errorMessage().strip();
        AiErrorExplanationModels.Explanation unavailable = new AiErrorExplanationModels.Explanation(
                code, originalMessage, "UNAVAILABLE", "", List.of(), false);
        if (!PUBLIC_MESSAGES.containsKey(code)) {
            // 未知码可能是客户端拼接的敏感内容，不进入模型提示词或审计明细。
            safeAudit(context, traceId, "UNSUPPORTED_ERROR_CODE", "UNAVAILABLE", "ERROR_CODE_NOT_ALLOWED", null);
            return unavailable;
        }
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null
                || !permissions.allows(context, "AI", "SYSTEM", "VIEW")) {
            safeAudit(context, traceId, code, "UNAVAILABLE", "AI_PERMISSION_OR_SYSTEM_CONTEXT_REQUIRED", null);
            return unavailable;
        }
        Long grantId = activeGrant(context);
        if (grantId == null) {
            safeAudit(context, traceId, code, "UNAVAILABLE", "AI_MODEL_NOT_GRANTED", null);
            return unavailable;
        }
        String operation = SAFE_OPERATIONS.contains(input.operation().strip())
                ? input.operation().strip() : "业务操作";
        // 公开消息由服务端错误码映射，避免把客户端拼入的记录标题、载荷、地址或密钥外发。
        String publicMessage = PUBLIC_MESSAGES.getOrDefault(code, "");
        if (!safeAudit(context, traceId, code, "REQUESTED", null, grantId)) return unavailable;
        try {
            String prompt = json.valueToTree(Map.of("errorCode", code,
                    "publicMessage", publicMessage, "operation", operation)).toString();
            AiModelGateway.Completion completion = gateway.complete(context, grantId,
                    "你是系统业务错误说明助手。只根据给定的公开错误码、公开消息和操作名称解释，"
                            + "不要推测具体记录、请求内容、权限细节或内部实现。"
                            + "仅输出 JSON：{\"explanation\":\"一句中文原因\","
                            + "\"suggestions\":[\"用户可执行的第一步\",\"用户可执行的第二步\"]}。",
                    prompt, false, false);
            Parsed parsed = parse(completion.text());
            if (!safeAudit(context, traceId, code, "SUCCESS", null, grantId)) return unavailable;
            return new AiErrorExplanationModels.Explanation(code, originalMessage, "EXPLAINED",
                    parsed.explanation(), parsed.suggestions(), true);
        } catch (RuntimeException failure) {
            String reason = failure instanceof com.unique.unexamine.shared.manage.web.DomainException domain
                    ? domain.code() : "AI_ERROR_EXPLANATION_FAILED";
            safeAudit(context, traceId, code, "UNAVAILABLE", reason, grantId);
            return unavailable;
        }
    }

    private Long activeGrant(AuthenticatedContext context) {
        try {
            for (AiSystemModelGrant grant : grants.selectList(Wrappers.<AiSystemModelGrant>lambdaQuery()
                    .eq(AiSystemModelGrant::getSystemId, context.systemId())
                    .eq(AiSystemModelGrant::getPlatformId, context.platformId())
                    .eq(AiSystemModelGrant::getStatus, "ACTIVE")
                    .orderByDesc(AiSystemModelGrant::getId))) {
                AiModel model = models.selectById(grant.getModelId());
                if (model != null && "ACTIVE".equals(model.getStatus())
                        && Objects.equals(model.getPlatformId(), context.platformId())
                        && Objects.equals(model.getScopeType(), model.getCredentialScopeType())
                        && (!"SYSTEM".equals(model.getScopeType())
                        || Objects.equals(model.getSystemId(), context.systemId()))) {
                    return grant.getId();
                }
            }
        } catch (RuntimeException failure) {
            LOG.warn("读取系统 AI 错误解释模型失败：{}", failure.getClass().getSimpleName());
        }
        return null;
    }

    /** 模型结构异常只使说明降级，不覆盖原 API 错误。 */
    private Parsed parse(String content) {
        try {
            String text = content.strip();
            if (text.startsWith("```")) {
                int firstLine = text.indexOf('\n');
                int close = text.lastIndexOf("```");
                if (firstLine < 0 || close <= firstLine) throw new IllegalArgumentException();
                text = text.substring(firstLine + 1, close).strip();
            }
            JsonNode root = json.readTree(text);
            String explanation = root == null ? "" : root.path("explanation").asText("").strip();
            JsonNode suggestionsNode = root == null ? null : root.path("suggestions");
            if (explanation.isBlank() || explanation.length() > 500 || suggestionsNode == null
                    || !suggestionsNode.isArray() || suggestionsNode.isEmpty()
                    || suggestionsNode.size() > 3) throw new IllegalArgumentException();
            List<String> suggestions = new ArrayList<>();
            for (JsonNode item : suggestionsNode) {
                String suggestion = item.asText("").strip();
                if (!item.isTextual() || suggestion.isBlank() || suggestion.length() > 160) {
                    throw new IllegalArgumentException();
                }
                suggestions.add(suggestion);
            }
            return new Parsed(explanation, List.copyOf(suggestions));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("AI explanation response invalid", failure);
        }
    }

    /** 审计不保存原错误消息或模型文本；审计失败时不继续发起新的模型调用。 */
    private boolean safeAudit(AuthenticatedContext context, String traceId, String code,
            String status, String reason, Long grantId) {
        try {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("errorCode", code);
            detail.put("modelGrantId", grantId);
            detail.put("reason", reason);
            audit.record(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "AI_ERROR_EXPLANATION", "API_ERROR", code, status, detail);
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("AI 错误说明审计失败：{}", failure.getClass().getSimpleName());
            return false;
        }
    }

    private record Parsed(String explanation, List<String> suggestions) { }
}
