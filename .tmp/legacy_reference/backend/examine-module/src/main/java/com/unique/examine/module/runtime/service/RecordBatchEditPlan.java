package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

final class RecordBatchEditPlan {
    private static final int MAX_CHANGES = 20;
    private static final String FIELD_CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{0,63}$";

    static final String UPDATE_RECORD_SQL = """
            UPDATE un_module_record
               SET updated_at=?,updated_by=?,version=version+1
             WHERE system_id=? AND tenant_id=? AND logical_module_id=?
               AND schema_version_id=? AND module_snapshot_id=? AND record_id=?
               AND status='ACTIVE' AND version=?
            """;
    static final String DELETE_TOUCHED_SEARCH_SQL = """
            DELETE FROM un_module_record_search
             WHERE system_id=? AND tenant_id=? AND record_id=?
               AND schema_version_id=? AND module_snapshot_id=?
               AND logical_field_id IN (%s)
            """;
    static final String DELETE_TOUCHED_INDEX_SQL = """
            DELETE FROM un_module_record_index
             WHERE system_id=? AND tenant_id=? AND record_id=?
               AND schema_version_id=? AND module_snapshot_id=?
               AND logical_field_id IN (%s)
            """;
    static final String DELETE_TOUCHED_VALUE_SQL = """
            DELETE FROM un_module_record_value
             WHERE system_id=? AND tenant_id=? AND record_id=?
               AND schema_version_id=? AND module_snapshot_id=?
               AND field_snapshot_id IN (%s)
            """;
    static final String DELETE_TOUCHED_UNIQUE_SQL = """
            DELETE FROM un_module_record_unique
             WHERE system_id=? AND tenant_id=?
               AND schema_version_id=? AND module_snapshot_id=?
               AND record_id IN (%s) AND logical_field_id IN (%s)
            """;

    private final RecordBatchMutationPlan mutation;
    private final List<Change> changes;

    private RecordBatchEditPlan(RecordBatchMutationPlan mutation, List<Change> changes) {
        this.mutation = mutation;
        this.changes = List.copyOf(changes);
    }

    static RecordBatchEditPlan from(RecordRuntimeViews.BatchEditRequest request) {
        if (request == null) {
            throw invalid();
        }
        final RecordBatchMutationPlan mutation;
        try {
            mutation = RecordBatchMutationPlan.from(
                    new RecordRuntimeViews.BatchCommandRequest(request.items()),
                    1,
                    "edit");
        } catch (BusinessException exception) {
            if (!"BATCH_INVALID".equals(exception.code())) {
                throw exception;
            }
            throw invalid();
        }
        if (request.changes() == null
                || request.changes().isEmpty()
                || request.changes().size() > MAX_CHANGES) {
            throw invalid();
        }
        var changes = new ArrayList<Change>(request.changes().size());
        var fieldCodes = new HashSet<String>();
        for (var supplied : request.changes()) {
            if (supplied == null
                    || supplied.fieldCode() == null
                    || !supplied.fieldCode().matches(FIELD_CODE_PATTERN)
                    || !fieldCodes.add(supplied.fieldCode())
                    || supplied.operation() == null) {
                throw invalid();
            }
            final Operation operation;
            try {
                operation = Operation.valueOf(supplied.operation());
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
            if (operation == Operation.SET && (supplied.value() == null || supplied.value().isNull())
                    || operation == Operation.CLEAR && supplied.value() != null) {
                throw invalid();
            }
            changes.add(new Change(supplied.fieldCode(), operation, supplied.value()));
        }
        return new RecordBatchEditPlan(mutation, changes);
    }

    List<Long> lockOrder() {
        return mutation.lockOrder();
    }

    List<RecordBatchMutationPlan.Selection> requestOrder() {
        return mutation.requestOrder();
    }

    List<Change> changes() {
        return changes;
    }

    PreparedEdit preflight(List<RecordState> records) {
        var invalidStateIds = records.stream()
                .filter(record -> !record.currentSchema())
                .map(RecordState::recordId)
                .collect(java.util.stream.Collectors.toSet());
        var prepared = mutation.preflight(
                records.stream().map(record -> new RecordBatchMutationPlan.RecordState(
                        record.recordId(),
                        record.version(),
                        record.status())).toList(),
                Set.of("ACTIVE"),
                invalidStateIds);
        return new PreparedEdit(prepared);
    }

    enum Operation {
        SET,
        CLEAR
    }

    record Change(String fieldCode, Operation operation, JsonNode value) { }

    record RecordState(long recordId, long version, String status, boolean currentSchema) { }

    final class PreparedEdit {
        private final RecordBatchMutationPlan.PreparedBatch prepared;

        private PreparedEdit(RecordBatchMutationPlan.PreparedBatch prepared) {
            this.prepared = prepared;
        }

        RecordRuntimeViews.BatchMutationResponse apply(LongFunction<Long> edit) {
            return prepared.apply("ACTIVE", edit);
        }
    }

    static BusinessException invalid() {
        return new BusinessException(
                "BATCH_EDIT_INVALID",
                "Batch edit requires 1 to 200 unique records and 1 to 20 unique SET or CLEAR field changes",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
