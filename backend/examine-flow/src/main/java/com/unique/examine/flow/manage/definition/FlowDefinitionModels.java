package com.unique.examine.flow.manage.definition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Flow definition API request and response models.
 */
public final class FlowDefinitionModels {

    private FlowDefinitionModels() {
    }

    public record FlowQueryRequest(String status, String publishStatus, String boundModuleId, String keyword) {
    }

    public record FlowSaveRequest(String flowCode, String flowName, String boundModuleId,
                                  TriggerRule triggerRule, Integer status, CanvasSaveRequest canvas,
                                  String description) {
    }

    public record TriggerRule(String triggerType, List<String> actionCodes, String conditionExpression,
                              boolean manualStartAllowed, boolean idempotencyRequired) {
    }

    public record CanvasSaveRequest(List<NodeSaveRequest> nodes, List<EdgeSaveRequest> edges) {
    }

    public record NodeSaveRequest(String nodeKey, String nodeType, String nodeName, PositionVO position,
                                  Map<String, Object> propertyPayload, Integer status) {
    }

    public record EdgeSaveRequest(String edgeKey, String sourceNodeKey, String targetNodeKey, String branchLabel,
                                  ConditionExpression conditionPayload) {
    }

    public record FlowDefinitionVO(String flowId, String systemId, String tenantId, String flowCode,
                                   String flowName, String boundModuleId, TriggerRule triggerRule,
                                   Integer status, String publishStatus, String currentVersion, CanvasVO canvas,
                                   List<NodePropertyPanelVO> propertyPanels, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
    }

    public record CanvasVO(String flowId, List<FlowNodeConfigVO> nodes, List<FlowEdgeVO> edges,
                           List<BranchLabelVO> branchLabels, CanvasValidationSummary validationSummary) {
    }

    public record FlowNodeConfigVO(String nodeKey, String nodeType, String nodeName, PositionVO position,
                                   Object propertyPayload, Integer status, String propertyPanelCode,
                                   boolean runtimeExecutable) {
    }

    public record FlowEdgeVO(String edgeKey, String sourceNodeKey, String targetNodeKey, String branchLabel,
                             ConditionExpression conditionPayload) {
    }

    public record BranchLabelVO(String edgeKey, String sourceNodeKey, String branchLabel, boolean defaultBranch,
                                Integer sort) {
    }

    public record PositionVO(Integer x, Integer y, Integer width, Integer height) {
    }

    public record CanvasValidationSummary(boolean connected, boolean hasEndNode, List<String> warnings) {
    }

    public record NodeLibraryItem(String nodeType, String nodeTypeName, String category, String icon,
                                  String description, Object defaultPropertyPayload,
                                  List<PropertyFieldMeta> propertySchema, List<String> requiredCapabilities) {
    }

    public record NodePropertyPanelVO(String nodeType, String panelCode, String title, String payloadType,
                                      List<PropertyFieldMeta> fields, List<String> supportedActions) {
    }

    public record PropertyFieldMeta(String fieldCode, String fieldName, String fieldType, boolean required,
                                    List<String> options, Object defaultValue, String description) {
    }

    public record ApprovalPropertyPayload(String approvalType, String assigneeType, List<String> assigneeIds,
                                          boolean allowTransfer, boolean allowReject, boolean reasonRequired,
                                          FieldPermissionPolicy fieldPermissionPolicy,
                                          TimeoutPolicy timeoutPolicy) {
    }

    public record FieldPermissionPolicy(List<String> readableFieldCodes, List<String> writableFieldCodes,
                                        String deniedFieldMode, String permissionVersion) {
    }

    public record TimeoutPolicy(Integer dueMinutes, String timeoutAction, String reminderNodeKey) {
    }

    public record ConditionPropertyPayload(String ruleMode, List<ConditionExpression> expressions,
                                           String defaultBranchLabel, String unmatchedPolicy) {
    }

    public record ConditionExpression(String expressionId, String fieldCode, String operator, Object expectedValue,
                                      String expressionText) {
    }

    public record FieldUpdatePropertyPayload(String updateMode, List<FieldUpdateRule> updates,
                                             boolean permissionCheckRequired, boolean auditReasonRequired) {
    }

    public record FieldUpdateRule(String fieldCode, String updateType, Object value, String valueExpression) {
    }

    public record ExternalApiPropertyPayload(String externalAppRefId, String endpointCode, String method,
                                             Map<String, String> requestMapping, Map<String, String> responseMapping,
                                             RetryPolicy retryPolicy, String idempotencyKeyField,
                                             String failurePolicy) {
    }

    public record RetryPolicy(Integer maxAttempts, Integer intervalSeconds, boolean compensationRequired) {
    }

    public record TimerPropertyPayload(String timerMode, Integer delayMinutes, String cronExpression,
                                       String timezone, Integer triggerLimit, String businessCalendarCode) {
    }

    public record TimeoutReminderPropertyPayload(String sourceApprovalNodeKey, Integer dueMinutes,
                                                 List<String> reminderChannels, Integer maxRemindTimes,
                                                 String escalationAssigneeType, String messageTemplateCode) {
    }

    public record EndPropertyPayload(String endStatus, boolean closeTodos, String writeBackFieldCode,
                                     String messageTemplateCode) {
    }

    public record FlowSimulationRequest(String moduleId, String recordId, String versionNo,
                                        Map<String, Object> fieldValues, String actorMemberId,
                                        String startNodeKey, String idempotencyKey) {
    }

    public record FlowSimulationResult(String simulationId, String flowId, String versionNo, boolean passed,
                                       List<SimulationStepTrace> stepTraces,
                                       List<ConditionDecisionVO> conditionDecisions,
                                       List<SimulationApproverVO> predictedApprovers,
                                       List<PublishCheckItem> failureItems,
                                       List<PublishCheckItem> blockerItems,
                                       List<ImpactRef> impactRefs,
                                       boolean runtimeInstanceCreated,
                                       String traceId, LocalDateTime createdAt) {
    }

    public record SimulationStepTrace(Integer sequence, String nodeKey, String nodeName, String nodeType,
                                      String inputSummary, String outputSummary, List<String> nextNodeKeys,
                                      Long elapsedMs) {
    }

    public record SimulationApproverVO(String nodeKey, String nodeName, String assigneeType,
                                       List<String> assigneeIds, String displayName) {
    }

    public record ConditionDecisionVO(String nodeKey, String expressionId, String selectedEdgeKey,
                                      String branchLabel, boolean matched, Map<String, Object> evaluatedValues) {
    }

    public record PublishRequest(String reason, String idempotencyKey) {
    }

    public record PublishCheckResultVO(boolean passed, List<PublishCheckItem> failureItems,
                                       List<PublishCheckItem> warningItems, List<ImpactRef> impactRefs,
                                       String traceId, String auditLogId) {
    }

    public record PublishCheckItem(String itemCode, String itemName, String severity, String objectType,
                                   String objectId, String message, String fixAction) {
    }

    public record ImpactRef(String objectType, String objectId, String name, String impactType) {
    }

    public record PublishResult(String result, String flowId, String version, String snapshotId, String traceId,
                                String auditLogId, String asyncTaskId, FlowSnapshotVO snapshot,
                                LocalDateTime operatedAt) {
    }

    public record FlowSnapshotVO(String snapshotId, String flowId, String versionNo, List<FlowNodeConfigVO> nodes,
                                 List<FlowEdgeVO> edges, PublishCheckResultVO publishCheckResult,
                                 String publishedBy, LocalDateTime publishedAt, String traceId,
                                 boolean immutable) {
    }

    public record ImpactAnalysisVO(String flowId, String versionNo, List<ImpactRef> impactRefs,
                                   Integer affectedModuleCount, Integer affectedRecordEstimate,
                                   Integer affectedRunningInstanceCount, List<String> warnings,
                                   String traceId, LocalDateTime checkedAt) {
    }
}
