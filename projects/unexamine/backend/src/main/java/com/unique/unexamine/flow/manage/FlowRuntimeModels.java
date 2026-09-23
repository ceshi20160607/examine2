package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowRuntimeModels {
    private FlowRuntimeModels() {
    }

    /** 运行入口仅展示当前范围已发布流程的名称与版本，不暴露设计草稿和节点配置。 */
    public record FlowCatalogEntry(Long id, String name, Long currentVersionId,
                                   Integer currentVersionNumber) {
    }

    /** 当前作用域的最小人员目录，不携带账号凭证、联系方式或完整角色信息。 */
    public record ParticipantDirectory(
            List<com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels.Department> departments,
            List<ParticipantView> people, long permissionVersion) { }

    public record ParticipantView(Long tenantMemberId, Long accountId, String displayName,
                                  Long departmentId, String departmentName, String positionTitle) { }

    public record StartRequest(
            @NotNull Long flowId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 100) String businessType,
            @Size(max = 100) String businessId,
            Map<String, Object> businessSnapshot,
            Map<String, Object> variables,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record HandleTaskRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey,
            Map<String, Object> variables,
            @JsonAlias("targetAccountId") Long targetTenantMemberId,
            Map<String, com.fasterxml.jackson.databind.JsonNode> fieldValues,
            Long targetPlatformAccountId,
            Long expectedFormRevision,
            LocalDateTime delegationEndTime,
            List<Long> attachmentIds) {
        /** 表单修订号用于会签候选确认；旧无表单调用保留原接口。 */
        public HandleTaskRequest(String actionCode, String comment, String idempotencyKey,
                Map<String, Object> variables, Long targetTenantMemberId,
                Map<String, com.fasterxml.jackson.databind.JsonNode> fieldValues, Long targetPlatformAccountId) {
            this(actionCode, comment, idempotencyKey, variables, targetTenantMemberId, fieldValues,
                    targetPlatformAccountId, null, null, null);
        }
        /** 表单修订号调用不要求知道后来增加的委托截止时间。 */
        public HandleTaskRequest(String actionCode, String comment, String idempotencyKey,
                Map<String, Object> variables, Long targetTenantMemberId,
                Map<String, com.fasterxml.jackson.databind.JsonNode> fieldValues, Long targetPlatformAccountId,
                Long expectedFormRevision) {
            this(actionCode, comment, idempotencyKey, variables, targetTenantMemberId, fieldValues,
                    targetPlatformAccountId, expectedFormRevision, null, null);
        }
        /** 已有系统处理调用仍使用工作空间成员，不受新增平台身份参数影响。 */
        public HandleTaskRequest(String actionCode, String comment, String idempotencyKey,
                Map<String, Object> variables, Long targetTenantMemberId,
                Map<String, com.fasterxml.jackson.databind.JsonNode> fieldValues) {
            this(actionCode, comment, idempotencyKey, variables, targetTenantMemberId, fieldValues, null);
        }
        /** 原待办快捷处理没有节点输入，仍交由相同处理入口校验候选值和必填。 */
        public HandleTaskRequest(String actionCode, String comment, String idempotencyKey,
                Map<String, Object> variables, Long targetTenantMemberId) {
            this(actionCode, comment, idempotencyKey, variables, targetTenantMemberId, null);
        }
    }

    /** 任务绑定表单只公开当前允许编辑的字段，原提交意图与正式记录均不会因读取而改变。 */
    public record TaskFormContext(Long taskId, Long instanceId, String moduleCode, Long recordId,
                                  String actionCode, com.unique.unexamine.runtimedata.manage.RuntimeRecordEditContext form,
                                  String message, Long formRevision) {
        /** 没有候选表单的旧调用不伪造候选修订号。 */
        public TaskFormContext(Long taskId, Long instanceId, String moduleCode, Long recordId,
                String actionCode, com.unique.unexamine.runtimedata.manage.RuntimeRecordEditContext form, String message) {
            this(taskId, instanceId, moduleCode, recordId, actionCode, form, message, null);
        }
    }

    public record InstanceActionRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @NotBlank @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record IncidentActionRequest(
            @NotBlank @Size(max = 32) String actionCode,
            @NotBlank @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodeRequest(
            @NotNull @JsonAlias("assigneeAccountId") Long assigneeTenantMemberId,
            @NotBlank @Size(max = 32) String position,
            @NotBlank @Size(max = 2000) String reason,
            Map<String, Map<String, String>> statusMappings,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodePreview(
            Long instanceId, String instanceStatus, String currentNodeKey, Long suspendedTaskId,
            Long assigneeTenantMemberId, String assigneeName, String assigneeDepartment,
            String position, String reason,
            Map<String, Map<String, String>> statusMappings,
            boolean allowed, List<String> checks) {
    }

    public record ManualNodeResult(Long taskId, Long actionId, InstanceView instance) {
    }

    /** 人员标识使用当前身份域：系统工作空间成员或平台账号，不接受客户端执行图。 */
    public record RuntimeNodeRequest(
            @NotBlank @Size(max = 32) String nodeType,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 32) String position,
            @Size(max = 255) String anchorNodeKey,
            @NotNull @Size(min = 1, max = 100) List<Long> assigneeIds,
            @NotBlank @Size(max = 32) String approvalMode,
            @NotBlank @Size(max = 2000) String reason,
            @NotNull Integer expectedInstanceVersion,
            @NotNull Long expectedGraphRevision,
            @NotBlank @Size(max = 255) String idempotencyKey) { }

    /** 预览披露冻结继承和人员解析结果；提交仍会重查当前权限、人员及双修订号。 */
    public record RuntimeNodePreview(Long instanceId, Integer instanceVersion, long graphRevision,
            String currentNodeKey, String currentNodeName, String anchorNodeKey, String anchorNodeName,
            String nodeType, String name, String position,
            String approvalMode, List<ParticipantView> assignees, String reason, boolean allowed,
            List<String> checks) { }

    /** 服务端从实例实际进入记录排除历史路径后的可选位置，不允许客户端自行扩大锚点目录。 */
    public record RuntimeNodeAnchor(String nodeKey, String name, String nodeType,
            boolean beforeAllowed, boolean afterAllowed) { }

    public record CandidateView(Long id, String candidateType, Long tenantMemberId, Long accountId,
                                String displayName, String departmentName, String positionTitle,
                                String resolutionReason, LocalDateTime createdAt) {
    }

    /** 当前处理授权由服务端复用真实任务动作检查，不由客户端把历史候选重新解释为授权。 */
    public record TaskView(Long id, String nodeKey, String taskType, String status, boolean currentHandler,
                           Long assigneeTenantMemberId, String assigneeName,
                           String assigneeDepartment, String assigneePosition,
                           Map<String, Object> assigneeSnapshot,
                           LocalDateTime dueAt, LocalDateTime claimedAt, LocalDateTime completedAt,
                           Integer version, boolean attachmentRequired, int minimumAttachmentCount,
                           List<CandidateView> candidates) {
    }

    public record ActionView(Long id, Long taskId, String nodeKey, String actionCode,
                             String comment, Map<String, Object> input, Map<String, Object> result,
                             String idempotencyKey, Long actedByTenantMemberId, String actedByName,
                             LocalDateTime actedAt) {
    }

    public record ExceptionView(Long id, String nodeKey, String exceptionType, String errorCode,
                                String errorMessage, String policyAction, String status,
                                Long resolvedByTenantMemberId, String resolvedByName, String resolutionComment,
                                LocalDateTime occurredAt, LocalDateTime resolvedAt, Integer version) {
    }

    public record ExecutionView(Long id, Long parentExecutionId, String nodeKey, String nodeType,
                                String status, LocalDateTime enteredAt, LocalDateTime leftAt,
                                String resultCode, Integer version) {
    }

    /** 父子流程关系只公开流程名称、版本和运行状态，不公开变量映射或子流程业务数据。 */
    public record SubflowView(String relation, Long instanceId, Long executionId, String nodeKey,
                              Long flowId, String flowName, Long flowVersionId, Integer flowVersionNumber,
                              String status, String errorCode, boolean viewable) {
    }

    public record HistoryEventView(Long id, Long executionId, Long taskId, String nodeKey,
                                   String eventType, String eventName, Long actorTenantMemberId,
                                   String actorName, String beforeStatus, String afterStatus,
                                   Map<String, Object> detail, LocalDateTime occurredAt) {
    }

    public record JobView(Long id, Long executionId, String nodeKey, String jobType, String status,
                          Integer attemptCount, Integer maxAttempts, LocalDateTime nextRunAt,
                          String lastErrorCode, String lastErrorMessage, LocalDateTime completedAt,
                          Integer version) {
    }

    public record InstanceView(
            Long id,
            String contextType,
            Long platformId,
            Long systemId,
            Long tenantId,
            Long flowId,
            Long flowVersionId,
            Integer flowVersionNumber,
            String definitionHash,
            Map<String, Object> definitionSnapshot,
            String businessType,
            String businessId,
            Map<String, Object> businessSnapshot,
            com.unique.unexamine.runtimedata.manage.RuntimeBusinessView businessView,
            String title,
            String currentNodeKey,
            String currentNodeName,
            String status,
            Long startedByTenantMemberId,
            boolean startedByCurrentUser,
            String startedByName,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage,
            Integer version,
            long graphRevision,
            List<RuntimeNodeAnchor> runtimeNodeAnchors,
            Map<String, Object> variables,
            List<TaskView> tasks,
            List<ActionView> actions,
            List<ExceptionView> exceptions,
            List<ExecutionView> executions,
            List<SubflowView> subflows,
            List<HistoryEventView> history,
            List<JobView> jobs,
            List<String> allowedActions,
            // 仅当前处理人的冻结意见要求，不暴露完整节点配置。
            List<String> opinionRequiredActions,
            String nextStep) {
    }

    public record ActionResult(Long actionId, boolean replayed, InstanceView instance) {
    }

    /** 本人业务申请的最小列表摘要，不包含业务快照、私有字段、变量和来源组织配置。 */
    public record BusinessActionApplication(Long actionId, String moduleCode, String actionCode,
            String flowName, String status, String businessStatus, String mode, String currentNodeName,
            LocalDateTime startTime, LocalDateTime finishTime) {}

    /** 游标以内部事件顺序翻页；过滤无权申请后空页仍可继续加载剩余候选。 */
    public record BusinessActionApplicationsPage(List<BusinessActionApplication> items, Long nextCursor) {}

    /** 原业务动作发起人的只读审批进度；不包含来源定义、变量、候选人目录或原始输入。 */
    public record BusinessActionProgress(
            Long actionId, String moduleCode, String actionCode, String mode, String businessStatus,
            Long flowInstanceId, String flowName, Integer flowVersionNumber, String status,
            String currentNodeName, LocalDateTime startTime, LocalDateTime finishTime,
            List<ProgressNode> nodes, List<ProgressEvent> history,
            // 只有当前 DETAIL 及字段策略同时允许时才返回业务数据，不能使用旧 Flow 快照代替。
            com.unique.unexamine.runtimedata.manage.RuntimeRecordView record,
            com.unique.unexamine.runtimedata.manage.RuntimeBusinessView businessView, String message) {
    }

    /** 已进入节点的名称和执行状态，不返回审批条件、状态映射或动作配置。 */
    public record ProgressNode(String nodeKey, String name, String nodeType, String status,
                               LocalDateTime enterTime, LocalDateTime leaveTime) {
    }

    /** 仅保留系统生成的审批历史名称、状态与时间，省略详细载荷和组织身份。 */
    public record ProgressEvent(String nodeName, String eventName, String beforeStatus,
                                String afterStatus, LocalDateTime occurredTime) {
    }
}
