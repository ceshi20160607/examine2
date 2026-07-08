package com.unique.examine.module.manage.runtime;

import com.unique.examine.core.api.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Runtime dynamic record API request and response models.
 */
public final class RuntimeRecordModels {

    private RuntimeRecordModels() {
    }

    public record RecordSearchRequest(Integer pageNo, Integer pageSize, String keyword, String sceneCode,
                                      List<FieldFilterCriterion> fieldFilters,
                                      List<RecordSortCriterion> sorts) {
    }

    public record FieldFilterCriterion(String fieldCode, String operator, Object value) {
    }

    public record RecordSortCriterion(String fieldCode, String direction) {
    }

    public record RuntimeRecordSearchVO(RuntimeListSchemaVO listSchema, PageResult<BusinessRecordRow> page,
                                        AppliedQueryMeta appliedQuery, DataScopeView dataScope,
                                        List<String> serverChecks) {
    }

    public record RuntimeListSchemaVO(String moduleId, String moduleCode, String sceneCode,
                                      List<ColumnPermissionVO> columns, List<FilterCapabilityVO> filters,
                                      List<SortCapabilityVO> sorters, RuntimePageMeta page,
                                      RowDetailTarget rowDetailTarget, List<ActionView> toolbarActions,
                                      List<ActionView> rowActions, List<ActionView> batchActions,
                                      DataScopeView dataScope, String permissionSnapshotId,
                                      String permissionSnapshotVersion, String schemaVersion) {
    }

    public record RuntimeSceneOptionVO(String sceneId, String sceneCode, String sceneName, boolean defaultScene) {
    }

    public record ColumnPermissionVO(String fieldId, String fieldCode, String label, Integer width,
                                     boolean visibleDefault, boolean sortable, boolean writable,
                                     boolean fixed, String permissionMode, String maskRule, String disabledReason) {
    }

    public record FilterCapabilityVO(String fieldId, String fieldCode, String label, String fieldType,
                                     List<String> operators, boolean quickFilter, boolean advanced,
                                     boolean indexSupported, String permissionMode, String disabledReason) {
    }

    public record SortCapabilityVO(String fieldId, String fieldCode, String label, List<String> directions,
                                   String defaultDirection, boolean defaultSort, boolean indexSupported,
                                   String disabledReason) {
    }

    public record RuntimePageMeta(int defaultPageSize, List<Integer> allowedPageSizes, boolean serverSide,
                                  boolean cursorSupported) {
    }

    public record RowDetailTarget(String targetType, String route, String drawerCode, boolean preserveListContext,
                                  String clickBoundary) {
    }

    public record ActionView(String actionCode, String actionName, String actionType, String position,
                             boolean enabled, String disabledReason, SelectionLimit selectionLimit,
                             ResultHook resultHook, boolean idempotencyRequired) {
    }

    public record SelectionLimit(String selectionMode, Integer minSelected, Integer maxSelected,
                                 List<String> requiredStatuses, boolean sameTenantRequired,
                                 String forbiddenReason) {
    }

    public record ResultHook(String resultType, String drawerCode, boolean returnsAuditLog,
                             boolean returnsAsyncTask, String traceField, List<String> states) {
    }

    public record DataScopeView(String systemId, String tenantId, String systemMemberId, String scopeType,
                                String expression, boolean enforced, String permissionSnapshotVersion) {
    }

    public record AppliedQueryMeta(String keyword, String sceneCode,
                                   List<FieldFilterCriterion> acceptedFilters,
                                   List<FieldFilterCriterion> rejectedFilters,
                                   List<RecordSortCriterion> acceptedSorts,
                                   List<RecordSortCriterion> rejectedSorts) {
    }

    public record BusinessRecordRow(String recordId, String title, String summary, List<FieldValueView> fields,
                                    List<ActionView> actions, Map<String, String> disabledReasons,
                                    RowDetailTarget rowDetailTarget, DataScopeView dataScope,
                                    String permissionSnapshotVersion, LocalDateTime updatedAt) {
    }

    public record FieldValueView(String fieldCode, String label, Object value, String displayValue,
                                 String permissionMode, String maskRule, boolean writable,
                                 String disabledReason) {
    }

    public record BusinessDetailView(RecordSummary summary, List<FieldValueView> baseFields,
                                     List<DetailTabView> tabs, ApprovalSidebarHook approvalSidebar,
                                     List<FieldMaskResult> fieldMaskResults, List<ActionView> actions,
                                     RowDetailTarget rowDetailTarget, DataScopeView dataScope,
                                     HistoryHook historyHook, String permissionSnapshotVersion) {
    }

    public record RecordSummary(String recordId, String title, String summary, String status, String statusColor,
                                String ownerMemberId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record DetailTabView(String tabCode, String tabName, boolean visible, String permissionCode,
                                String disabledReason, Map<String, Object> payload) {
    }

    public record ApprovalSidebarHook(boolean visible, String flowInstanceId, String currentNodeName,
                                      String pendingTaskId, String status, String actionTarget,
                                      String disabledReason) {
    }

    public record FieldMaskResult(String fieldCode, String permissionMode, String maskRule,
                                  boolean originalValueVisible, String desensitizedValue,
                                  String reason) {
    }

    public record HistoryHook(String endpoint, boolean serverSidePage, String permissionCode) {
    }

    public record RecordSaveRequest(Map<String, Object> fieldValues,
                                    Map<String, List<Map<String, Object>>> childRows,
                                    List<String> attachmentIds, String draftId, String sourceType) {
    }

    public record RecordMutationResult(String recordId, String result, List<FieldChangeView> changedFields,
                                       List<FieldMaskResult> fieldMaskResults, RowDetailTarget rowDetailTarget,
                                       ApprovalSidebarHook approvalSidebar, String permissionSnapshotId,
                                       String permissionSnapshotVersion, String idempotencyKey, String traceId,
                                       String auditLogId, LocalDateTime operatedAt) {
    }

    public record FieldChangeView(String fieldCode, Object beforeValue, Object afterValue,
                                  String permissionMode, String reason) {
    }

    public record ActionExecutionRequest(Map<String, Object> parameters, List<String> selectedRecordIds,
                                         String reason, String sourceType) {
    }

    public record ActionExecutionResult(String recordId, String actionCode, String result, boolean accepted,
                                        String disabledReason, ResultHook resultHook, AsyncTaskRef asyncTask,
                                        String permissionSnapshotVersion, String idempotencyKey, String traceId,
                                        String auditLogId, LocalDateTime operatedAt) {
    }

    public record AsyncTaskRef(String taskId, String bizType, String status, Integer progress,
                               boolean retryable, boolean cancelable, String resultFileId,
                               String errorFileId) {
    }

    public record RecordHistoryEntry(String historyId, String recordId, String actionCode,
                                     List<FieldChangeView> fieldDiff, String sourceType,
                                     String permissionSnapshotId, String permissionSnapshotVersion,
                                     List<FieldMaskResult> desensitizeResult, String traceId,
                                     String auditLogId, String operatedBy, LocalDateTime operatedAt) {
    }

    public record RuntimePrintRequest(String templateCode) {
    }
}
