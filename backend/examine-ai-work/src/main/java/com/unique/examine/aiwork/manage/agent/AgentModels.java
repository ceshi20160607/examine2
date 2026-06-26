package com.unique.examine.aiwork.manage.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Agent API 请求和响应模型。
 */
public final class AgentModels {

    private AgentModels() {
    }

    /**
     * 平台模型授权保存请求。
     *
     * @param authorizationCode 授权编码
     * @param modelProvider 模型供应商
     * @param modelName 模型名称
     * @param modelCredentialRef 模型密钥引用
     * @param quota 配额
     * @param dataOutboundPolicy 外发数据策略
     * @param status 状态
     * @param idempotencyKey 幂等键
     */
    public record ModelAuthorizationSaveRequest(String authorizationCode, String modelProvider, String modelName,
                                                SecretRefVO modelCredentialRef, QuotaConfigVO quota,
                                                DataOutboundPolicyVO dataOutboundPolicy, Integer status,
                                                String idempotencyKey) {
    }

    /**
     * 模型授权视图。
     *
     * @param authorizationId 授权 ID
     * @param authorizationCode 授权编码
     * @param modelProvider 模型供应商
     * @param modelName 模型名称
     * @param modelCredentialRef 模型密钥引用
     * @param quota 配额
     * @param dataOutboundPolicy 外发数据策略
     * @param status 状态
     * @param version 版本
     * @param operation 操作结果
     * @param createdAt 创建时间
     */
    public record ModelAuthorizationVO(String authorizationId, String authorizationCode, String modelProvider,
                                       String modelName, SecretRefVO modelCredentialRef, QuotaConfigVO quota,
                                       DataOutboundPolicyVO dataOutboundPolicy, Integer status, String version,
                                       OperationResultVO operation, LocalDateTime createdAt) {
    }

    /**
     * 密钥引用。
     *
     * @param secretRefId 密钥引用 ID
     * @param refType 引用类型
     * @param version 版本
     * @param expiresAt 到期时间
     * @param rotationStatus 轮换状态
     * @param lastUsedAt 最近使用时间
     * @param displayName 展示名称
     */
    public record SecretRefVO(String secretRefId, String refType, String version, LocalDateTime expiresAt,
                              String rotationStatus, LocalDateTime lastUsedAt, String displayName) {
    }

    /**
     * 模型配额。
     *
     * @param tokenLimit 每日 token 上限
     * @param requestLimit 每日请求上限
     * @param costLimit 每日成本上限
     * @param resetPolicy 重置策略
     */
    public record QuotaConfigVO(Integer tokenLimit, Integer requestLimit, Integer costLimit, String resetPolicy) {
    }

    /**
     * 外发数据策略。
     *
     * @param allowExternalModel 是否允许外部模型
     * @param allowedRegions 允许区域
     * @param outboundFields 外发字段范围
     * @param retentionDays 外发快照保留天数
     */
    public record DataOutboundPolicyVO(boolean allowExternalModel, List<String> allowedRegions,
                                       List<String> outboundFields, Integer retentionDays) {
    }

    /**
     * Agent 策略保存请求。
     *
     * @param policyCode 策略编码
     * @param scope 策略范围
     * @param status 状态
     * @param idempotencyKey 幂等键
     */
    public record AgentPolicySaveRequest(String policyCode, AgentPolicyScopeVO scope, Integer status,
                                         String idempotencyKey) {
    }

    /**
     * Agent 策略视图。
     *
     * @param policyId 策略 ID
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param policyCode 策略编码
     * @param scope 策略范围
     * @param status 状态
     * @param publishStatus 发布状态
     * @param operation 操作结果
     * @param createdAt 创建时间
     */
    public record AgentPolicyVO(String policyId, String systemId, String tenantId, String policyCode,
                                AgentPolicyScopeVO scope, Integer status, PublishStatusVO publishStatus,
                                OperationResultVO operation, LocalDateTime createdAt) {
    }

    /**
     * AgentPolicyScope。
     *
     * @param moduleScope 可访问模块范围
     * @param fieldScope 可读写字段范围
     * @param actionScope 可执行动作范围
     * @param dataScopeExpression 数据范围表达式
     * @param outboundLimit 外发限制
     * @param desensitizePolicy 脱敏策略
     * @param policyVersion 策略版本
     */
    public record AgentPolicyScopeVO(List<String> moduleScope, Map<String, List<String>> fieldScope,
                                     List<String> actionScope, String dataScopeExpression,
                                     OutboundLimitVO outboundLimit, DesensitizePolicyVO desensitizePolicy,
                                     String policyVersion) {
    }

    /**
     * 外发限制。
     *
     * @param maxRows 单次最大行数
     * @param allowFileExport 是否允许文件导出
     * @param allowThirdPartyWebhook 是否允许第三方 Webhook
     */
    public record OutboundLimitVO(Integer maxRows, boolean allowFileExport, boolean allowThirdPartyWebhook) {
    }

    /**
     * 脱敏策略。
     *
     * @param mode 模式
     * @param maskedFields 脱敏字段
     * @param preview 预览
     */
    public record DesensitizePolicyVO(String mode, List<String> maskedFields, Map<String, String> preview) {
    }

    /**
     * 发布状态。
     *
     * @param status 状态
     * @param version 版本
     * @param publishable 是否可发布
     * @param lastPublishedAt 最近发布时间
     */
    public record PublishStatusVO(String status, String version, boolean publishable, LocalDateTime lastPublishedAt) {
    }

    /**
     * 策略发布检查。
     *
     * @param policyId 策略 ID
     * @param passed 是否通过
     * @param targetVersion 目标版本
     * @param items 检查项
     * @param impactRefs 影响对象
     * @param traceId 链路追踪 ID
     */
    public record AgentPolicyPublishCheckVO(String policyId, boolean passed, String targetVersion,
                                            List<PublishCheckItemVO> items, List<ImpactRefVO> impactRefs,
                                            String traceId) {
    }

    /**
     * 发布检查项。
     *
     * @param itemCode 检查编码
     * @param itemName 检查名称
     * @param level 等级
     * @param passed 是否通过
     * @param message 说明
     */
    public record PublishCheckItemVO(String itemCode, String itemName, String level, boolean passed,
                                     String message) {
    }

    /**
     * 影响对象。
     *
     * @param refType 对象类型
     * @param refId 对象 ID
     * @param refName 对象名称
     */
    public record ImpactRefVO(String refType, String refId, String refName) {
    }

    /**
     * 创建会话请求。
     *
     * @param authorizationCode 模型授权编码
     * @param policyCode 策略编码
     * @param promptVersion 提示词版本
     * @param openingQuestion 首问
     * @param metadata 扩展元数据
     */
    public record AgentSessionCreateRequest(String authorizationCode, String policyCode, String promptVersion,
                                            String openingQuestion, Map<String, Object> metadata) {
    }

    /**
     * 会话视图。
     *
     * @param sessionId 会话 ID
     * @param scope platform 或 system
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param systemMemberId 系统成员 ID
     * @param modelVersion 模型版本
     * @param promptVersion 提示词版本
     * @param policyVersion 策略版本
     * @param permissionSnapshotId 权限快照
     * @param status 状态
     * @param boundary 边界说明
     * @param createdAt 创建时间
     */
    public record AgentSessionVO(String sessionId, String scope, String systemId, String tenantId,
                                 String systemMemberId, String modelVersion, String promptVersion,
                                 String policyVersion, String permissionSnapshotId, String status,
                                 AgentBoundaryVO boundary, LocalDateTime createdAt) {
    }

    /**
     * 会话消息请求。
     *
     * @param message 用户消息
     * @param toolHints 工具提示
     * @param idempotencyKey 幂等键
     */
    public record AgentMessageRequest(String message, List<String> toolHints, String idempotencyKey) {
    }

    /**
     * 会话消息响应。
     *
     * @param sessionId 会话 ID
     * @param role 角色
     * @param content 内容
     * @param toolCalls 工具调用
     * @param proposedConfirmations 推荐确认
     * @param audit 审计快照
     * @param operation 操作结果
     */
    public record AgentMessageResultVO(String sessionId, String role, String content, List<ToolCallVO> toolCalls,
                                       List<ConfirmationSummaryVO> proposedConfirmations, AgentAuditLogVO audit,
                                       OperationResultVO operation) {
    }

    /**
     * 工具调用。
     *
     * @param toolName 工具名称
     * @param status 状态
     * @param inputSnapshot 输入快照
     * @param outputSnapshot 输出快照
     */
    public record ToolCallVO(String toolName, String status, Map<String, Object> inputSnapshot,
                             Map<String, Object> outputSnapshot) {
    }

    /**
     * 确认摘要。
     *
     * @param confirmationId 确认 ID
     * @param confirmType 确认类型
     * @param status 状态
     * @param manualConfirmRequired 是否必须人工确认
     */
    public record ConfirmationSummaryVO(String confirmationId, String confirmType, String status,
                                        boolean manualConfirmRequired) {
    }

    /**
     * Agent 边界。
     *
     * @param allowedTargetTypes 允许目标类型
     * @param deniedTargetTypes 禁止目标类型
     * @param reason 说明
     */
    public record AgentBoundaryVO(List<String> allowedTargetTypes, List<String> deniedTargetTypes, String reason) {
    }

    /**
     * 平台 Agent 确认请求。
     *
     * @param sessionId 会话 ID
     * @param sourceConversation 原始对话
     * @param platformActions 平台动作
     * @param targetScope 目标范围
     * @param humanConfirmed 是否人工确认
     * @param idempotencyKey 幂等键
     */
    public record PlatformAgentConfirmRequest(String sessionId, String sourceConversation,
                                              List<PlatformAgentActionDraftVO> platformActions,
                                              String targetScope, Boolean humanConfirmed, String idempotencyKey) {
    }

    /**
     * 平台 Agent 动作草稿。
     *
     * @param actionType 动作类型
     * @param title 标题
     * @param payload 载荷
     */
    public record PlatformAgentActionDraftVO(String actionType, String title, Map<String, Object> payload) {
    }

    /**
     * 平台 Agent 确认结果。
     *
     * @param confirmation 确认视图
     * @param allowed 是否允许执行
     * @param allowedTargetTypes 允许目标
     * @param rejectedItems 拒绝项
     * @param generatedObjects 生成对象
     */
    public record PlatformAgentConfirmResultVO(AgentConfirmationVO confirmation, boolean allowed,
                                               List<String> allowedTargetTypes, List<RejectedItemVO> rejectedItems,
                                               List<GeneratedObjectVO> generatedObjects) {
    }

    /**
     * 系统写入确认创建请求。
     *
     * @param sessionId 会话 ID
     * @param sourceConversation 原始对话
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @param fieldDiffs 字段差异
     * @param permissionSnapshotId 权限快照
     * @param approvalRequired 是否需要审批
     * @param compensationPlan 失败补偿
     * @param humanConfirmed 是否人工确认
     * @param idempotencyKey 幂等键
     */
    public record SystemWriteConfirmationRequest(String sessionId, String sourceConversation, String moduleId,
                                                 String recordId, List<FieldDiffVO> fieldDiffs,
                                                 String permissionSnapshotId, Boolean approvalRequired,
                                                 String compensationPlan, Boolean humanConfirmed,
                                                 String idempotencyKey) {
    }

    /**
     * 系统写入确认处理请求。
     *
     * @param reason 原因
     * @param idempotencyKey 幂等键
     */
    public record ConfirmationActionRequest(String reason, String idempotencyKey) {
    }

    /**
     * 系统写入确认结果。
     *
     * @param confirmation 确认视图
     * @param fieldDiffs 字段差异
     * @param permissionClips 权限裁剪
     * @param approvalRequired 是否需要审批
     * @param compensationPlan 补偿方案
     * @param businessLogId 业务日志 ID
     */
    public record SystemWriteConfirmationVO(AgentConfirmationVO confirmation, List<FieldDiffVO> fieldDiffs,
                                            List<PermissionClipVO> permissionClips, boolean approvalRequired,
                                            String compensationPlan, String businessLogId) {
    }

    /**
     * 工作草稿确认请求。
     *
     * @param sessionId 会话 ID
     * @param sourceConversation 原始对话
     * @param draftType 草稿类型
     * @param draftPayload 草稿载荷
     * @param sourceSnapshot 来源快照
     * @param humanConfirmed 是否人工确认
     * @param idempotencyKey 幂等键
     */
    public record WorkDraftConfirmRequest(String sessionId, String sourceConversation, String draftType,
                                          Map<String, Object> draftPayload, List<WorkDraftSourceVO> sourceSnapshot,
                                          Boolean humanConfirmed, String idempotencyKey) {
    }

    /**
     * 工作草稿确认结果。
     *
     * @param confirmation 确认视图
     * @param draftType 草稿类型
     * @param draftPayload 草稿载荷
     * @param sourceSnapshot 来源快照
     * @param manualConfirmRequired 是否必须人工确认
     */
    public record WorkDraftConfirmResultVO(AgentConfirmationVO confirmation, String draftType,
                                           Map<String, Object> draftPayload,
                                           List<WorkDraftSourceVO> sourceSnapshot,
                                           boolean manualConfirmRequired) {
    }

    /**
     * 确认视图。
     *
     * @param confirmationId 确认 ID
     * @param confirmType 确认类型
     * @param status 状态
     * @param sourceConversation 原始对话
     * @param permissionSnapshotId 权限快照
     * @param confirmedBy 确认人
     * @param confirmedAt 确认时间
     * @param audit 审计日志
     * @param operation 操作结果
     */
    public record AgentConfirmationVO(String confirmationId, String confirmType, String status,
                                      String sourceConversation, String permissionSnapshotId, String confirmedBy,
                                      LocalDateTime confirmedAt, AgentAuditLogVO audit,
                                      OperationResultVO operation) {
    }

    /**
     * 字段差异。
     *
     * @param fieldCode 字段编码
     * @param fieldName 字段名称
     * @param beforeValue 修改前
     * @param afterValue 修改后
     * @param writable 是否可写
     * @param disabledReason 禁用原因
     */
    public record FieldDiffVO(String fieldCode, String fieldName, Object beforeValue, Object afterValue,
                              boolean writable, String disabledReason) {
    }

    /**
     * 权限裁剪。
     *
     * @param fieldCode 字段编码
     * @param clipType 裁剪类型
     * @param reason 原因
     */
    public record PermissionClipVO(String fieldCode, String clipType, String reason) {
    }

    /**
     * 工作草稿来源。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源 ID
     * @param title 标题
     * @param permissionPolicy 权限策略
     * @param included 是否纳入
     */
    public record WorkDraftSourceVO(String sourceType, String sourceId, String title, String permissionPolicy,
                                    boolean included) {
    }

    /**
     * 拒绝项。
     *
     * @param itemId 项 ID
     * @param itemType 项类型
     * @param reason 原因
     */
    public record RejectedItemVO(String itemId, String itemType, String reason) {
    }

    /**
     * 生成对象。
     *
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param title 标题
     * @param status 状态
     */
    public record GeneratedObjectVO(String objectType, String objectId, String title, String status) {
    }

    /**
     * Agent 审计查询。
     *
     * @param scope 范围
     * @param sessionId 会话 ID
     * @param confirmationId 确认 ID
     * @param modelVersion 模型版本
     * @param policyVersion 策略版本
     * @param permissionSnapshotId 权限快照
     * @param traceId 链路追踪 ID
     * @param keyword 关键字
     */
    public record AgentAuditLogQuery(String scope, String sessionId, String confirmationId, String modelVersion,
                                     String policyVersion, String permissionSnapshotId, String traceId,
                                     String keyword) {
    }

    /**
     * Agent 审计日志。
     *
     * @param logId 日志 ID
     * @param sessionId 会话 ID
     * @param confirmationId 确认 ID
     * @param scope 范围
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param modelVersion 模型版本
     * @param promptVersion 提示词版本
     * @param policyVersion 策略版本
     * @param permissionSnapshotId 权限快照
     * @param conversationSnapshot 原始对话快照
     * @param toolCallSnapshot 工具调用快照
     * @param desensitizeResult 脱敏结果
     * @param outboundSnapshot 外发数据快照
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     * @param createdAt 创建时间
     */
    public record AgentAuditLogVO(String logId, String sessionId, String confirmationId, String scope,
                                  String systemId, String tenantId, String modelVersion, String promptVersion,
                                  String policyVersion, String permissionSnapshotId,
                                  Map<String, Object> conversationSnapshot,
                                  Map<String, Object> toolCallSnapshot,
                                  Map<String, Object> desensitizeResult,
                                  Map<String, Object> outboundSnapshot, String traceId, String auditLogId,
                                  LocalDateTime createdAt) {
    }

    /**
     * 操作结果。
     *
     * @param operation 操作
     * @param idempotencyKey 幂等键
     * @param result 结果
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     * @param disabledReason 禁用原因
     * @param operatedAt 操作时间
     */
    public record OperationResultVO(String operation, String idempotencyKey, String result, String traceId,
                                    String auditLogId, String disabledReason, LocalDateTime operatedAt) {
    }
}
