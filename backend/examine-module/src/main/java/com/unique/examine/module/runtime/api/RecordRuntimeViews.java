package com.unique.examine.module.runtime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecordRuntimeViews {
    private RecordRuntimeViews() { }

    public record QueryLimits(int defaultSize, int maxSize, int maxSorts) { }

    /** Immutable member-runtime projection of the active published page/rule snapshot. */
    public record PublishedRuntimeContract(
            String schemaVersionId,
            List<PublishedPage> pages,
            List<PublishedRule> rules
    ) {
        public PublishedRuntimeContract {
            pages = pages == null ? List.of() : List.copyOf(pages);
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        public static PublishedRuntimeContract empty(String schemaVersionId) {
            return new PublishedRuntimeContract(schemaVersionId, List.of(), List.of());
        }
    }

    public record PublishedPage(
            String pageId,
            String pageCode,
            String type,
            String density,
            int columns,
            int gap,
            String labelPosition,
            boolean stickyActions,
            int pageSize,
            List<PageSection> sections,
            List<PageFieldPlacement> fields
    ) {
        public PublishedPage {
            sections = sections == null ? List.of() : List.copyOf(sections);
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record PageSection(
            String sectionCode,
            String title,
            int sortOrder,
            int columns,
            boolean collapsible,
            boolean collapsed
    ) { }

    public record PageFieldPlacement(
            String fieldCode,
            String sectionCode,
            int sortOrder,
            int gridRow,
            int gridColumn,
            int gridSpan,
            int width,
            String fixed
    ) { }

    public record PublishedRule(
            String ruleCode,
            String type,
            int priority,
            PublishedCondition condition,
            List<PublishedEffect> effects
    ) {
        public PublishedRule {
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublishedCondition(
            String join,
            String fieldCode,
            String operator,
            JsonNode value,
            List<PublishedCondition> children
    ) {
        public PublishedCondition {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record PublishedEffect(
            String effect,
            String targetCode,
            String targetAction,
            boolean value
    ) { }

    /** Server-side decision for one concrete create/edit/detail value state. */
    public record RuntimeRuleDecision(
            Set<String> hiddenFields,
            Set<String> requiredFields,
            Set<String> readonlyFields,
            Set<String> disabledActions,
            boolean deleteAllowed,
            boolean approvalRequired
    ) {
        public RuntimeRuleDecision {
            hiddenFields = hiddenFields == null ? Set.of() : Set.copyOf(hiddenFields);
            requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
            readonlyFields = readonlyFields == null ? Set.of() : Set.copyOf(readonlyFields);
            disabledActions = disabledActions == null ? Set.of() : Set.copyOf(disabledActions);
        }
    }

    public record ReferenceOption(String value, String label, String parentValue) { }

    public record FieldCapability(
            String fieldCode,
            String fieldName,
            String logicalFieldId,
            String type,
            String mode,
            boolean readable,
            boolean writable,
            boolean sensitiveReadable,
            boolean sensitiveQueryable,
            boolean masked,
            List<String> operators,
            boolean sortable,
            boolean showInList,
            boolean showInDetail,
            List<ReferenceOption> options,
            JsonNode schema
    ) {
        public FieldCapability {
            operators = List.copyOf(operators);
            options = List.copyOf(options);
        }
    }

    public record CreateRecordRequest(
            String schemaVersionId,
            String title,
            Map<String, JsonNode> values,
            List<RelationInput> relations,
            List<SubtableInput> subtables
    ) {
        public CreateRecordRequest {
            values = values == null ? Map.of() : Map.copyOf(values);
            relations = relations == null ? List.of() : List.copyOf(relations);
            subtables = subtables == null ? List.of() : List.copyOf(subtables);
        }
    }

    public record RelationTargetInput(String targetRecordId, long targetExpectedVersion, int ordinal) { }

    public record RelationInput(String fieldCode, List<RelationTargetInput> targets) {
        public RelationInput {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    public record SubRowInput(
            String clientRowKey,
            String rowId,
            Long expectedVersion,
            int ordinal,
            Map<String, JsonNode> values
    ) {
        public SubRowInput {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record SubtableInput(String fieldCode, List<SubRowInput> rows) {
        public SubtableInput {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record OperationCapability(String permissionCode, boolean enabled, String disabledReason) { }

    public record CompositionCapabilities(
            OperationCapability relationRead,
            OperationCapability relationAdd,
            OperationCapability relationRemove,
            OperationCapability relationReorder,
            OperationCapability subtableRead,
            OperationCapability subtableAdd,
            OperationCapability subtableUpdate,
            OperationCapability subtableRemove,
            OperationCapability subtableReorder
    ) { }

    public record RelationItem(String targetRecordId, long targetVersion, int ordinal, String title) { }

    public record RelationCandidatePage(
            List<RelationItem> items,
            int page,
            int size,
            long total,
            String correlationId
    ) {
        public RelationCandidatePage {
            items = List.copyOf(items);
        }
    }

    public record RelationPage(
            List<RelationItem> items,
            int page,
            int size,
            long total,
            CompositionCapabilities capabilities,
            String correlationId
    ) {
        public RelationPage {
            items = List.copyOf(items);
        }
    }

    public record RelationMutationRequest(
            long expectedVersion,
            List<RelationTargetInput> add,
            List<String> remove,
            List<String> order
    ) {
        public RelationMutationRequest {
            add = add == null ? List.of() : List.copyOf(add);
            remove = remove == null ? List.of() : List.copyOf(remove);
            order = order == null ? List.of() : List.copyOf(order);
        }
    }

    public record SubRowResponse(String rowId, long version, int ordinal, List<FieldValue> values) {
        public SubRowResponse {
            values = List.copyOf(values);
        }
    }

    public record SubtablePage(
            List<SubRowResponse> items,
            int page,
            int size,
            long total,
            CompositionCapabilities capabilities,
            String correlationId
    ) {
        public SubtablePage {
            items = List.copyOf(items);
        }
    }

    public record SubRowCreateInput(String clientRowKey, int ordinal, Map<String, JsonNode> values) {
        public SubRowCreateInput {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record SubRowUpdateInput(String rowId, long expectedVersion, int ordinal, Map<String, JsonNode> values) {
        public SubRowUpdateInput {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record SubRowRef(String rowId, long expectedVersion) { }

    public record SubtableMutationRequest(
            long expectedVersion,
            List<SubRowCreateInput> add,
            List<SubRowUpdateInput> update,
            List<SubRowRef> remove,
            List<String> order
    ) {
        public SubtableMutationRequest {
            add = add == null ? List.of() : List.copyOf(add);
            update = update == null ? List.of() : List.copyOf(update);
            remove = remove == null ? List.of() : List.copyOf(remove);
            order = order == null ? List.of() : List.copyOf(order);
        }
    }

    public record ReferenceRetryRequest(long expectedVersion) { }

    public record ReferenceRetryResponse(
            String recordId,
            long recordVersion,
            String fieldCode,
            String recalculationState,
            String correlationId
    ) { }

    public record RecordMutationResponse(
            String recordId,
            long version,
            String schemaVersionId,
            String status,
            String historyId,
            String correlationId
    ) { }

    public record VersionCommandRequest(long expectedVersion) { }

    public record BatchRecordRef(String recordId, long expectedVersion) { }

    public record BatchCommandRequest(List<BatchRecordRef> items) { }

    public record BatchTransferRequest(List<BatchRecordRef> items, String targetMemberId) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchFieldChange(String fieldCode, String operation, JsonNode value) { }

    public record BatchEditRequest(List<BatchRecordRef> items, List<BatchFieldChange> changes) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchMutationItem(
            String recordId,
            String resultCode,
            Long newVersion,
            String status,
            Long currentVersion
    ) {
        public static BatchMutationItem applied(String recordId, long newVersion, String status) {
            return new BatchMutationItem(recordId, "APPLIED", newVersion, status, null);
        }

        public static BatchMutationItem rejected(String recordId, String resultCode, Long currentVersion) {
            return new BatchMutationItem(recordId, resultCode, null, null, currentVersion);
        }
    }

    public record BatchMutationResponse(boolean allApplied, List<BatchMutationItem> items) {
        public BatchMutationResponse {
            items = List.copyOf(items);
        }
    }

    public record UpdateRecordRequest(
            String schemaVersionId,
            String title,
            long expectedVersion,
            Map<String, JsonNode> values,
            List<RelationInput> relations,
            List<SubtableInput> subtables
    ) {
        public UpdateRecordRequest {
            values = values == null ? Map.of() : Map.copyOf(values);
            relations = relations == null ? List.of() : List.copyOf(relations);
            subtables = subtables == null ? List.of() : List.copyOf(subtables);
        }
    }

    public record AutosaveRecordRequest(
            String schemaVersionId,
            String title,
            long expectedVersion,
            Map<String, JsonNode> values
    ) {
        public AutosaveRecordRequest {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record RecordSchema(
            String schemaVersionId,
            String moduleSnapshotId,
            String logicalModuleId,
            String checksum,
            String runtimeState,
            String unavailableReason,
            long authzEpoch,
            List<FieldCapability> fields,
            List<String> actions,
            QueryLimits queryLimits,
            PublishedRuntimeContract publishedRuntime
    ) {
        public RecordSchema {
            fields = List.copyOf(fields);
            actions = List.copyOf(actions);
            publishedRuntime = publishedRuntime == null
                    ? PublishedRuntimeContract.empty(schemaVersionId) : publishedRuntime;
        }

        public RecordSchema(
                String schemaVersionId,
                String moduleSnapshotId,
                String logicalModuleId,
                String checksum,
                String runtimeState,
                String unavailableReason,
                long authzEpoch,
                List<FieldCapability> fields,
                List<String> actions,
                QueryLimits queryLimits
        ) {
            this(schemaVersionId, moduleSnapshotId, logicalModuleId, checksum, runtimeState,
                    unavailableReason, authzEpoch, fields, actions, queryLimits,
                    PublishedRuntimeContract.empty(schemaVersionId));
        }
    }

    public record FieldValue(
            String fieldCode,
            String fieldName,
            String type,
            Object value,
            String displayValue,
            String failureCorrelationId
    ) {
        public FieldValue(String fieldCode, String fieldName, String type, Object value, String displayValue) {
            this(fieldCode, fieldName, type, value, displayValue, null);
        }
    }

    public record SortAnchor(List<JsonNode> values, String recordId) {
        public SortAnchor {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record RecordSummary(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<FieldValue> values,
            SortAnchor sortAnchor
    ) {
        public RecordSummary {
            values = List.copyOf(values);
        }

        public RecordSummary(
                String recordId,
                String recordNo,
                long version,
                String status,
                String title,
                List<FieldValue> values
        ) {
            this(recordId, recordNo, version, status, title, values, null);
        }
    }

    public record RecordPage(
            List<RecordSummary> rows,
            int page,
            int size,
            long total,
            String queryHash,
            String querySnapshotToken,
            List<String> invalidNodes
    ) {
        public RecordPage {
            rows = List.copyOf(rows);
            invalidNodes = invalidNodes == null ? List.of() : List.copyOf(invalidNodes);
        }

        public RecordPage(List<RecordSummary> rows, int page, int size, long total) {
            this(rows, page, size, total, null, null, List.of());
        }
    }

    public record MyDraftsQueryRequest(int page, int size, String q) { }

    public record NeighborRequest(
            String direction,
            String querySnapshotToken,
            SortAnchor sortAnchor
    ) {
    }

    public record NeighborResponse(
            RecordSummary neighbor,
            boolean boundary,
            String querySnapshotToken,
            String correlationId
    ) {
    }

    public record RecordDetail(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            String schemaVersionId,
            List<FieldValue> values,
            List<String> actions
    ) {
        public RecordDetail {
            values = List.copyOf(values);
            actions = List.copyOf(actions);
        }
    }

    public record AutosaveRecordResponse(
            RecordDetail record,
            String lastSavedAt,
            String expiresAt
    ) { }

    public record VersionConflictData(
            long currentVersion,
            RecordDetail currentSnapshot,
            List<String> conflictFields
    ) {
        public VersionConflictData {
            conflictFields = List.copyOf(conflictFields);
        }
    }
}
