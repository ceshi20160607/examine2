package com.unique.examine.module.runtime.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Narrow module-owned boundary for externally authenticated record calls.
 *
 * <p>Application scope checks and signature authentication belong to the
 * OpenAPI transport. This facade turns the resulting machine principal into
 * the ordinary runtime session so published schema, current member
 * permissions, field masking and data-scope SQL have one owner.</p>
 */
@Component
public final class OpenApiRecordFacade {
    private static final Set<String> SYSTEM_FIELD_TYPES = Set.of(
            "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");
    private static final Set<String> RESERVED_VALUE_KEYS = Set.of(
            "id", "recordid", "recordno", "status", "lifecyclestate", "title", "version",
            "schemaversionid", "modulesnapshotid", "logicalmoduleid", "systemid", "tenantid",
            "accountid", "memberid", "servicememberid", "ownermemberid", "ownerdepartmentid",
            "createdat", "createdby", "updatedat", "updatedby", "deletedat", "deletedby",
            "draftexpiresat", "priorstatus", "requestid", "traceid", "applicationid");

    private final OpenApiRecordRuntime runtime;

    @Autowired
    public OpenApiRecordFacade(RecordRuntimeService records) {
        this(new RuntimeAdapter(records));
    }

    OpenApiRecordFacade(OpenApiRecordRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public RecordView create(
            Session session,
            String moduleCode,
            CreateCommand command,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid("OPENAPI_RECORD_REQUEST_INVALID", "body", "Record create body is required");
        }
        var runtimeSession = runtimeSession(session);
        var schema = runtime.schema(runtimeSession, moduleCode);
        validateValues(schema, command.values());
        return record(runtime.create(
                runtimeSession,
                moduleCode,
                session.applicationId(),
                command.lifecycleState(),
                command.values(),
                idempotencyKey,
                session.requestId(),
                session.traceId()));
    }

    public RecordView detail(Session session, String moduleCode, String recordId) {
        Objects.requireNonNull(session, "session");
        return record(runtime.detail(runtimeSession(session), moduleCode, positiveRecordId(recordId)));
    }

    public RecordView update(
            Session session,
            String moduleCode,
            String recordId,
            UpdateCommand command,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid("OPENAPI_RECORD_REQUEST_INVALID", "body", "Record update body is required");
        }
        var runtimeSession = runtimeSession(session);
        var schema = runtime.schema(runtimeSession, moduleCode);
        validateValues(schema, command.values());
        return record(runtime.update(
                runtimeSession,
                moduleCode,
                positiveRecordId(recordId),
                session.applicationId(),
                command.expectedVersion(),
                command.values(),
                idempotencyKey,
                session.requestId(),
                session.traceId()));
    }

    public RecordView activate(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey
    ) {
        return lifecycle(session, moduleCode, recordId, command, idempotencyKey, "activate");
    }

    public RecordView archive(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey
    ) {
        return lifecycle(session, moduleCode, recordId, command, idempotencyKey, "archive");
    }

    public RecordView unarchive(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey
    ) {
        return lifecycle(session, moduleCode, recordId, command, idempotencyKey, "unarchive");
    }

    public RecordView trash(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey
    ) {
        return lifecycle(session, moduleCode, recordId, command, idempotencyKey, "trash");
    }

    public RecordView restoreFromTrash(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey
    ) {
        return lifecycle(session, moduleCode, recordId, command, idempotencyKey, "restore-from-trash");
    }

    private RecordView lifecycle(
            Session session,
            String moduleCode,
            String recordId,
            VersionCommand command,
            String idempotencyKey,
            String action
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid("OPENAPI_RECORD_REQUEST_INVALID", "body", "Lifecycle command body is required");
        }
        return record(runtime.lifecycle(
                runtimeSession(session),
                moduleCode,
                positiveRecordId(recordId),
                session.applicationId(),
                action,
                command.expectedVersion(),
                idempotencyKey,
                session.requestId(),
                session.traceId()));
    }

    public PageView list(Session session, String moduleCode, int page, int size) {
        Objects.requireNonNull(session, "session");
        if (page < 1 || page > 1_000_000 || size < 1 || size > 200) {
            throw invalid("QUERY_INVALID", "page", "page must be within 1..1000000 and size within 1..200");
        }
        var result = runtime.list(runtimeSession(session), moduleCode, page, size);
        return new PageView(
                result.rows().stream().map(OpenApiRecordFacade::summary).toList(),
                result.page(), result.size(), result.total());
    }

    public RelationPageView relations(
            Session session,
            String moduleCode,
            String recordId,
            String fieldCode,
            int page,
            int size
    ) {
        Objects.requireNonNull(session, "session");
        validateCompositionPage(page, size);
        return relationPage(runtime.relations(
                runtimeSession(session),
                moduleCode,
                positiveRecordId(recordId),
                fieldCode,
                page,
                size,
                session.traceId()
        ));
    }

    public SubtablePageView subtable(
            Session session,
            String moduleCode,
            String recordId,
            String fieldCode,
            int page,
            int size
    ) {
        Objects.requireNonNull(session, "session");
        validateCompositionPage(page, size);
        return subtablePage(runtime.subtable(
                runtimeSession(session),
                moduleCode,
                positiveRecordId(recordId),
                fieldCode,
                page,
                size,
                session.traceId()
        ));
    }

    public MutationReceipt mutateRelation(
            Session session,
            String moduleCode,
            String recordId,
            String fieldCode,
            RelationMutationCommand command,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid(
                    "OPENAPI_RELATION_REQUEST_INVALID",
                    "body",
                    "Relation mutation body is required");
        }
        requireIdempotencyKey(idempotencyKey);
        var request = new RecordRuntimeViews.RelationMutationRequest(
                command.expectedVersion(),
                command.add().stream()
                        .map(target -> new RecordRuntimeViews.RelationTargetInput(
                                target.targetRecordId(),
                                target.targetExpectedVersion(),
                                target.ordinal()))
                        .toList(),
                command.remove(),
                command.order());
        return receipt(runtime.mutateRelation(
                runtimeSession(session),
                moduleCode,
                positiveRecordId(recordId),
                fieldCode,
                session.applicationId(),
                request,
                idempotencyKey,
                session.requestId(),
                session.traceId()
        ));
    }

    public MutationReceipt mutateSubtable(
            Session session,
            String moduleCode,
            String recordId,
            String fieldCode,
            SubtableMutationCommand command,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid(
                    "OPENAPI_SUBTABLE_REQUEST_INVALID",
                    "body",
                    "Subtable mutation body is required");
        }
        requireIdempotencyKey(idempotencyKey);
        var request = new RecordRuntimeViews.SubtableMutationRequest(
                command.expectedVersion(),
                command.add().stream()
                        .map(row -> new RecordRuntimeViews.SubRowCreateInput(
                                row.clientRowKey(), row.ordinal(), row.values()))
                        .toList(),
                command.update().stream()
                        .map(row -> new RecordRuntimeViews.SubRowUpdateInput(
                                row.rowId(), row.expectedVersion(),
                                row.ordinal(), row.values()))
                        .toList(),
                command.remove().stream()
                        .map(row -> new RecordRuntimeViews.SubRowRef(
                                row.rowId(), row.expectedVersion()))
                        .toList(),
                command.order());
        return receipt(runtime.mutateSubtable(
                runtimeSession(session),
                moduleCode,
                positiveRecordId(recordId),
                fieldCode,
                session.applicationId(),
                request,
                idempotencyKey,
                session.requestId(),
                session.traceId()
        ));
    }

    private static void validateValues(
            RecordRuntimeViews.RecordSchema schema,
            Map<String, JsonNode> values
    ) {
        var fields = new HashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> fields.put(field.fieldCode(), field));
        var errors = new ArrayList<ApiError>();
        for (var entry : values.entrySet()) {
            var code = entry.getKey();
            var path = "values." + code;
            if (code == null || !code.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                errors.add(new ApiError("OPENAPI_FIELD_CODE_INVALID", path, "A stable field code is required"));
                continue;
            }
            var field = fields.get(code);
            if (RESERVED_VALUE_KEYS.contains(canonicalKey(code))
                    || field != null && SYSTEM_FIELD_TYPES.contains(field.type())) {
                errors.add(new ApiError("SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN", path,
                        "System, ownership, tenant and audit fields are server managed"));
            } else if (field == null) {
                errors.add(new ApiError("OPENAPI_FIELD_UNKNOWN", path,
                        "The field is not present in the current readable schema"));
            } else if (!field.writable()) {
                errors.add(new ApiError("OPENAPI_FIELD_WRITE_FORBIDDEN", path,
                        "The current service member cannot write this field"));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("OPENAPI_RECORD_VALUES_INVALID",
                    "Record values are not writable in the current published schema",
                    HttpStatus.UNPROCESSABLE_ENTITY, errors);
        }
    }

    private static String canonicalKey(String value) {
        return value.replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static long positiveRecordId(String recordId) {
        try {
            var parsed = Long.parseLong(recordId);
            if (parsed <= 0 || !Long.toString(parsed).equals(recordId)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new BusinessException("RECORD_NOT_FOUND",
                    "Record does not exist or is outside the current data scope", HttpStatus.NOT_FOUND);
        }
    }

    private static RuntimeSession runtimeSession(Session session) {
        return new RuntimeSession(
                session.accountId(), session.systemId(), session.serviceMemberId(), session.tenantId(),
                session.permissions());
    }

    private static RecordView record(RecordRuntimeViews.RecordDetail value) {
        return new RecordView(
                value.recordId(), value.recordNo(), value.version(), value.status(), value.title(),
                value.schemaVersionId(), value.values().stream().map(OpenApiRecordFacade::field).toList());
    }

    private static SummaryView summary(RecordRuntimeViews.RecordSummary value) {
        return new SummaryView(
                value.recordId(), value.recordNo(), value.version(), value.status(), value.title(),
                value.values().stream().map(OpenApiRecordFacade::field).toList());
    }

    private static FieldValueView field(RecordRuntimeViews.FieldValue value) {
        return new FieldValueView(value.fieldCode(), value.fieldName(), value.type(), value.value(),
                value.displayValue(), value.failureCorrelationId());
    }

    private static RelationPageView relationPage(RecordRuntimeViews.RelationPage value) {
        return new RelationPageView(
                value.items().stream()
                        .map(item -> new RelationItemView(
                                item.targetRecordId(), item.targetVersion(),
                                item.ordinal(), item.title()))
                        .toList(),
                value.page(),
                value.size(),
                value.total(),
                capabilities(value.capabilities()),
                value.correlationId()
        );
    }

    private static SubtablePageView subtablePage(RecordRuntimeViews.SubtablePage value) {
        return new SubtablePageView(
                value.items().stream()
                        .map(row -> new SubtableRowView(
                                row.rowId(), row.version(), row.ordinal(),
                                row.values().stream()
                                        .map(OpenApiRecordFacade::field)
                                        .toList()))
                        .toList(),
                value.page(),
                value.size(),
                value.total(),
                capabilities(value.capabilities()),
                value.correlationId()
        );
    }

    private static CompositionCapabilitiesView capabilities(
            RecordRuntimeViews.CompositionCapabilities value
    ) {
        return new CompositionCapabilitiesView(
                capability(value.relationRead()),
                capability(value.relationAdd()),
                capability(value.relationRemove()),
                capability(value.relationReorder()),
                capability(value.subtableRead()),
                capability(value.subtableAdd()),
                capability(value.subtableUpdate()),
                capability(value.subtableRemove()),
                capability(value.subtableReorder())
        );
    }

    private static OperationCapabilityView capability(
            RecordRuntimeViews.OperationCapability value
    ) {
        return new OperationCapabilityView(
                value.permissionCode(), value.enabled(), value.disabledReason());
    }

    private static MutationReceipt receipt(
            RecordRuntimeViews.RecordMutationResponse value
    ) {
        return new MutationReceipt(
                value.recordId(),
                value.version(),
                value.schemaVersionId(),
                value.status(),
                value.historyId(),
                value.correlationId()
        );
    }

    private static void validateCompositionPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid(
                    "QUERY_INVALID",
                    "page",
                    "Composition page must use page>=1 and size 1..100");
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key is required",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static BusinessException invalid(String code, String path, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError(code, path, message)));
    }

    public record Session(
            long applicationId,
            long accountId,
            long systemId,
            long tenantId,
            long serviceMemberId,
            Set<String> permissions,
            String requestId,
            String traceId
    ) {
        public Session {
            if (applicationId <= 0 || accountId <= 0 || systemId <= 0 || tenantId <= 0 || serviceMemberId <= 0) {
                throw new IllegalArgumentException("OpenAPI record session ids must be positive");
            }
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
            requestId = requiredContextValue(requestId, "requestId");
            traceId = requiredContextValue(traceId, "traceId");
        }
    }

    public record CreateCommand(String lifecycleState, Map<String, JsonNode> values) {
        public CreateCommand {
            values = values == null ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    public record UpdateCommand(long expectedVersion, Map<String, JsonNode> values) {
        public UpdateCommand {
            values = values == null ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    public record VersionCommand(long expectedVersion) { }

    public record RelationTargetCommand(
            String targetRecordId,
            long targetExpectedVersion,
            int ordinal
    ) { }

    public record RelationMutationCommand(
            long expectedVersion,
            List<RelationTargetCommand> add,
            List<String> remove,
            List<String> order
    ) {
        public RelationMutationCommand {
            add = add == null ? List.of() : List.copyOf(add);
            remove = remove == null ? List.of() : List.copyOf(remove);
            order = order == null ? List.of() : List.copyOf(order);
        }
    }

    public record SubtableRowCreateCommand(
            String clientRowKey,
            int ordinal,
            Map<String, JsonNode> values
    ) {
        public SubtableRowCreateCommand {
            values = values == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    public record SubtableRowUpdateCommand(
            String rowId,
            long expectedVersion,
            int ordinal,
            Map<String, JsonNode> values
    ) {
        public SubtableRowUpdateCommand {
            values = values == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    public record SubtableRowReferenceCommand(
            String rowId,
            long expectedVersion
    ) { }

    public record SubtableMutationCommand(
            long expectedVersion,
            List<SubtableRowCreateCommand> add,
            List<SubtableRowUpdateCommand> update,
            List<SubtableRowReferenceCommand> remove,
            List<String> order
    ) {
        public SubtableMutationCommand {
            add = add == null ? List.of() : List.copyOf(add);
            update = update == null ? List.of() : List.copyOf(update);
            remove = remove == null ? List.of() : List.copyOf(remove);
            order = order == null ? List.of() : List.copyOf(order);
        }
    }

    public record FieldValueView(
            String fieldCode,
            String fieldName,
            String type,
            Object value,
            String displayValue,
            String failureCorrelationId
    ) { }

    public record RecordView(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            String schemaVersionId,
            List<FieldValueView> values
    ) {
        public RecordView {
            values = List.copyOf(values);
        }
    }

    public record SummaryView(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<FieldValueView> values
    ) {
        public SummaryView {
            values = List.copyOf(values);
        }
    }

    public record PageView(List<SummaryView> rows, int page, int size, long total) {
        public PageView {
            rows = List.copyOf(rows);
        }
    }

    public record RelationItemView(
            String targetRecordId,
            long targetVersion,
            int ordinal,
            String title
    ) { }

    public record RelationPageView(
            List<RelationItemView> items,
            int page,
            int size,
            long total,
            CompositionCapabilitiesView capabilities,
            String correlationId
    ) {
        public RelationPageView {
            items = List.copyOf(items);
        }
    }

    public record SubtableRowView(
            String rowId,
            long version,
            int ordinal,
            List<FieldValueView> values
    ) {
        public SubtableRowView {
            values = List.copyOf(values);
        }
    }

    public record SubtablePageView(
            List<SubtableRowView> items,
            int page,
            int size,
            long total,
            CompositionCapabilitiesView capabilities,
            String correlationId
    ) {
        public SubtablePageView {
            items = List.copyOf(items);
        }
    }

    public record OperationCapabilityView(
            String permissionCode,
            boolean enabled,
            String disabledReason
    ) { }

    public record CompositionCapabilitiesView(
            OperationCapabilityView relationRead,
            OperationCapabilityView relationAdd,
            OperationCapabilityView relationRemove,
            OperationCapabilityView relationReorder,
            OperationCapabilityView subtableRead,
            OperationCapabilityView subtableAdd,
            OperationCapabilityView subtableUpdate,
            OperationCapabilityView subtableRemove,
            OperationCapabilityView subtableReorder
    ) { }

    public record MutationReceipt(
            String recordId,
            long version,
            String schemaVersionId,
            String status,
            String historyId,
            String correlationId
    ) { }

    private static String requiredContextValue(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(name + " must contain 1..64 characters");
        }
        return value;
    }

    interface OpenApiRecordRuntime {
        RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode);

        RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                long applicationId,
                String lifecycleState,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId);

        RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                long expectedVersion,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId);

        RecordRuntimeViews.RecordDetail lifecycle(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                String action,
                long expectedVersion,
                String idempotencyKey,
                String requestId,
                String traceId);

        RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId);

        RecordRuntimeViews.RecordPage list(RuntimeSession session, String moduleCode, int page, int size);

        default RecordRuntimeViews.RelationPage relations(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            throw new UnsupportedOperationException();
        }

        default RecordRuntimeViews.SubtablePage subtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            throw new UnsupportedOperationException();
        }

        default RecordRuntimeViews.RecordMutationResponse mutateRelation(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.RelationMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            throw new UnsupportedOperationException();
        }

        default RecordRuntimeViews.RecordMutationResponse mutateSubtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.SubtableMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private record RuntimeAdapter(RecordRuntimeService records) implements OpenApiRecordRuntime {
        private RuntimeAdapter {
            Objects.requireNonNull(records, "records");
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            return records.schema(session, moduleCode);
        }

        @Override
        public RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                long applicationId,
                String lifecycleState,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.createOpenApi(session, moduleCode, applicationId, lifecycleState, values,
                    idempotencyKey, requestId, traceId);
        }

        @Override
        public RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                long expectedVersion,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.updateOpenApi(session, moduleCode, recordId, applicationId, expectedVersion, values,
                    idempotencyKey, requestId, traceId);
        }

        @Override
        public RecordRuntimeViews.RecordDetail lifecycle(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                String action,
                long expectedVersion,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.lifecycleOpenApi(session, moduleCode, recordId, applicationId, action, expectedVersion,
                    idempotencyKey, requestId, traceId);
        }

        @Override
        public RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId) {
            return records.detail(session, moduleCode, recordId);
        }

        @Override
        public RecordRuntimeViews.RecordPage list(
                RuntimeSession session,
                String moduleCode,
                int page,
                int size
        ) {
            return records.list(session, moduleCode, page, size, "ACTIVE", null, null);
        }

        @Override
        public RecordRuntimeViews.RelationPage relations(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            return records.relations(
                    session, moduleCode, recordId, fieldCode,
                    page, size, correlationId);
        }

        @Override
        public RecordRuntimeViews.SubtablePage subtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            return records.subtable(
                    session, moduleCode, recordId, fieldCode,
                    page, size, correlationId);
        }

        @Override
        public RecordRuntimeViews.RecordMutationResponse mutateRelation(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.RelationMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.mutateRelation(
                    session, moduleCode, recordId, fieldCode, applicationId,
                    request, idempotencyKey, requestId, traceId);
        }

        @Override
        public RecordRuntimeViews.RecordMutationResponse mutateSubtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.SubtableMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.mutateSubtable(
                    session, moduleCode, recordId, fieldCode, applicationId,
                    request, idempotencyKey, requestId, traceId);
        }
    }
}
