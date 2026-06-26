package com.unique.examine.flow.manage.approval;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批任务处理 API 模型。
 */
public final class ApprovalModels {

    private ApprovalModels() {
    }

    /**
     * 审批动作请求。
     *
     * @param idempotencyKey 前端或调用方生成的幂等键，同一任务同一动作重复提交时必须复用
     * @param comment 审批意见
     * @param reason 拒绝、转交、终止等动作的业务原因
     * @param transferTargetId 转交目标成员 ID
     * @param transferTargetName 转交目标成员名称
     * @param fieldValues 审批时可写字段值，受节点字段权限控制
     * @param permissionSnapshotId 调用方看到的权限快照版本
     */
    public record ApprovalActionRequest(String idempotencyKey, String comment, String reason,
                                        String transferTargetId, String transferTargetName,
                                        Map<String, Object> fieldValues, String permissionSnapshotId) {
    }

    /**
     * 审批动作结果。
     *
     * @param actionResultId 本次审批动作结果 ID
     * @param systemId 系统 ID
     * @param taskId 审批任务 ID
     * @param instanceId 流程实例 ID
     * @param actionCode 动作编码：approve/reject/transfer
     * @param taskStatus 动作后的任务状态
     * @param instanceStatus 动作后的实例状态
     * @param workflowAdvanced 是否推进了流程实例
     * @param duplicate 是否为幂等重复请求
     * @param idempotencyKey 命中的幂等键
     * @param actionLogId 审批动作审计日志 ID
     * @param auditLogId 业务审计日志 ID
     * @param traceId 链路追踪 ID
     * @param message 面向前端的处理结果说明
     * @param reason 拒绝或转交原因
     * @param transferTarget 转交目标人信息
     * @param nextTasks 推进后产生的下一批任务
     * @param operatedAt 操作时间
     */
    public record ApprovalActionResult(String actionResultId, String systemId, String taskId, String instanceId,
                                       String actionCode, String taskStatus, String instanceStatus,
                                       boolean workflowAdvanced, boolean duplicate, String idempotencyKey,
                                       String actionLogId, String auditLogId, String traceId, String message,
                                       String reason, ApprovalActor transferTarget,
                                       List<ApprovalTaskSnapshot> nextTasks, LocalDateTime operatedAt) {
    }

    /**
     * 审批任务快照。
     *
     * @param taskId 审批任务 ID
     * @param taskNo 审批任务编号
     * @param instanceId 流程实例 ID
     * @param nodeKey 当前节点编码
     * @param nodeName 当前节点名称
     * @param assignee 审批人
     * @param status 任务状态
     * @param dueAt 到期时间
     * @param actions 当前任务允许的动作
     * @param permissionSnapshotId 审批节点字段权限快照版本
     * @param traceId 任务创建链路追踪 ID
     */
    public record ApprovalTaskSnapshot(String taskId, String taskNo, String instanceId, String nodeKey,
                                       String nodeName, ApprovalActor assignee, String status, LocalDateTime dueAt,
                                       List<String> actions, String permissionSnapshotId, String traceId) {
    }

    /**
     * 审批参与人。
     *
     * @param memberId 系统成员 ID
     * @param memberName 成员名称
     * @param roleName 成员在当前审批中的角色名称
     * @param departmentName 成员所属部门名称
     */
    public record ApprovalActor(String memberId, String memberName, String roleName, String departmentName) {
    }
}
