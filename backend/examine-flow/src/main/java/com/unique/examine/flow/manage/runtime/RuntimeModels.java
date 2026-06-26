package com.unique.examine.flow.manage.runtime;

import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActor;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalTaskSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程运行态 API 模型。
 */
public final class RuntimeModels {

    private RuntimeModels() {
    }

    /**
     * 流程实例快照。
     *
     * @param instance 实例基本信息
     * @param nodes 节点运行态快照
     * @param currentTasks 当前待处理审批任务
     * @param history 历史流转记录
     * @param businessTarget 关联业务对象
     * @param permission 权限和字段可见性快照
     * @param trace 链路追踪信息
     * @param audit 审计摘要
     */
    public record WorkflowInstanceSnapshot(WorkflowInstanceVO instance, List<WorkflowNodeSnapshot> nodes,
                                           List<ApprovalTaskSnapshot> currentTasks,
                                           List<WorkflowHistoryItem> history,
                                           BusinessTargetRef businessTarget,
                                           WorkflowPermissionSnapshot permission,
                                           TraceInfo trace, AuditInfo audit) {
    }

    /**
     * 流程实例基础信息。
     *
     * @param instanceId 实例 ID
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param flowId 流程定义 ID
     * @param flowName 流程名称
     * @param versionNo 发布快照版本
     * @param status 实例状态
     * @param startedBy 发起人
     * @param startedAt 发起时间
     * @param updatedAt 最近更新时间
     */
    public record WorkflowInstanceVO(String instanceId, String systemId, String tenantId, String flowId,
                                     String flowName, String versionNo, String status, ApprovalActor startedBy,
                                     LocalDateTime startedAt, LocalDateTime updatedAt) {
    }

    /**
     * 节点运行态快照。
     *
     * @param nodeKey 节点编码
     * @param nodeName 节点名称
     * @param nodeType 节点类型
     * @param status 节点运行状态
     * @param enteredAt 进入节点时间
     * @param completedAt 完成节点时间
     * @param assigneeIds 当前节点处理人
     * @param branchLabel 命中的分支标签
     * @param outputSummary 节点输出摘要
     */
    public record WorkflowNodeSnapshot(String nodeKey, String nodeName, String nodeType, String status,
                                       LocalDateTime enteredAt, LocalDateTime completedAt,
                                       List<String> assigneeIds, String branchLabel, String outputSummary) {
    }

    /**
     * 流转历史记录。
     *
     * @param historyId 历史记录 ID
     * @param nodeKey 节点编码
     * @param actionCode 动作编码
     * @param actionName 动作名称
     * @param actor 操作人
     * @param reason 操作原因或审批意见
     * @param transferTarget 转交目标人
     * @param workflowAdvanced 是否推进流程
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     * @param operatedAt 操作时间
     */
    public record WorkflowHistoryItem(String historyId, String nodeKey, String actionCode, String actionName,
                                      ApprovalActor actor, String reason, ApprovalActor transferTarget,
                                      boolean workflowAdvanced, String traceId, String auditLogId,
                                      LocalDateTime operatedAt) {
    }

    /**
     * 关联业务目标。
     *
     * @param moduleId 模块 ID
     * @param moduleCode 模块编码
     * @param moduleName 模块名称
     * @param objectId 业务对象 ID
     * @param objectTitle 业务对象标题
     * @param target 页面跳转目标
     * @param summary 业务摘要字段
     */
    public record BusinessTargetRef(String moduleId, String moduleCode, String moduleName, String objectId,
                                    String objectTitle, TargetRef target, Map<String, Object> summary) {
    }

    /**
     * 页面跳转目标。
     *
     * @param targetType 目标类型
     * @param routeName 前端路由名称
     * @param params 路由参数
     */
    public record TargetRef(String targetType, String routeName, Map<String, String> params) {
    }

    /**
     * 权限快照。
     *
     * @param permissionSnapshotId 权限快照 ID
     * @param permissionVersion 权限版本
     * @param readableFieldCodes 可读字段编码
     * @param writableFieldCodes 可写字段编码
     * @param actionPermissions 当前实例可用动作权限
     * @param fieldMaskResults 字段脱敏结果
     * @param disabledReasons 禁用原因
     */
    public record WorkflowPermissionSnapshot(String permissionSnapshotId, String permissionVersion,
                                             List<String> readableFieldCodes, List<String> writableFieldCodes,
                                             Map<String, Boolean> actionPermissions,
                                             Map<String, String> fieldMaskResults,
                                             Map<String, String> disabledReasons) {
    }

    /**
     * 链路追踪信息。
     *
     * @param traceId 当前查询链路 ID
     * @param requestId 当前请求 ID
     * @param source 触发来源
     * @param idempotencyKey 发起流程时使用的幂等键
     */
    public record TraceInfo(String traceId, String requestId, String source, String idempotencyKey) {
    }

    /**
     * 审计摘要。
     *
     * @param auditLogId 当前查询审计 ID
     * @param createdBy 创建人 ID
     * @param updatedBy 最近更新人 ID
     * @param actionLogIds 相关审批动作审计日志 ID
     */
    public record AuditInfo(String auditLogId, String createdBy, String updatedBy, List<String> actionLogIds) {
    }
}
