package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

final class RecordBatchMutationPlan {
    private static final int MAX_ITEMS = 200;

    private final List<Selection> requestOrder;
    private final List<Long> lockOrder;
    private final String operation;

    private RecordBatchMutationPlan(List<Selection> requestOrder, String operation) {
        this.requestOrder = List.copyOf(requestOrder);
        this.lockOrder = requestOrder.stream()
                .map(Selection::recordId)
                .sorted()
                .toList();
        this.operation = operation;
    }

    static RecordBatchMutationPlan from(
            RecordRuntimeViews.BatchCommandRequest request,
            long minimumVersion,
            String operation
    ) {
        if (minimumVersion < 0 || operation == null || operation.isBlank()
                || request == null || request.items() == null
                || request.items().isEmpty() || request.items().size() > MAX_ITEMS) {
            throw invalid(operation, minimumVersion);
        }
        var selections = new ArrayList<Selection>(request.items().size());
        var uniqueIds = new HashSet<Long>();
        for (var item : request.items()) {
            if (item == null || item.recordId() == null
                    || !item.recordId().matches("^[1-9][0-9]*$")
                    || item.expectedVersion() < minimumVersion) {
                throw invalid(operation, minimumVersion);
            }
            final long recordId;
            try {
                recordId = Long.parseLong(item.recordId());
            } catch (NumberFormatException exception) {
                throw invalid(operation, minimumVersion);
            }
            if (!uniqueIds.add(recordId)) {
                throw invalid(operation, minimumVersion);
            }
            selections.add(new Selection(item.recordId(), recordId, item.expectedVersion()));
        }
        return new RecordBatchMutationPlan(selections, operation.trim());
    }

    List<Selection> requestOrder() {
        return requestOrder;
    }

    List<Long> lockOrder() {
        return lockOrder;
    }

    PreparedBatch preflight(List<RecordState> lockedRecords, Set<String> allowedStates) {
        return preflight(lockedRecords, allowedStates, Set.of());
    }

    PreparedBatch preflight(
            List<RecordState> lockedRecords,
            Set<String> allowedStates,
            Set<Long> additionalInvalidStateIds
    ) {
        if (allowedStates == null || allowedStates.isEmpty()) {
            throw new IllegalArgumentException("Batch mutation allowed states are required");
        }
        if (additionalInvalidStateIds == null) {
            throw new IllegalArgumentException("Additional invalid state ids are required");
        }
        var failures = failures(
                lockedRecords,
                Set.copyOf(allowedStates),
                Set.copyOf(additionalInvalidStateIds));
        if (!failures.isEmpty()) {
            throw new BusinessException(
                    "BATCH_PRECONDITION_FAILED",
                    preconditionMessage(),
                    HttpStatus.CONFLICT,
                    List.of(),
                    new RecordRuntimeViews.BatchMutationResponse(false, failures));
        }
        return new PreparedBatch();
    }

    private List<RecordRuntimeViews.BatchMutationItem> failures(
            List<RecordState> lockedRecords,
            Set<String> allowedStates,
            Set<Long> additionalInvalidStateIds
    ) {
        var byId = new HashMap<Long, RecordState>();
        for (var record : lockedRecords) {
            byId.put(record.recordId(), record);
        }
        var failures = new ArrayList<RecordRuntimeViews.BatchMutationItem>();
        for (var selection : requestOrder) {
            var record = byId.get(selection.recordId());
            if (record == null) {
                failures.add(RecordRuntimeViews.BatchMutationItem.rejected(
                        selection.externalRecordId(), "RECORD_NOT_FOUND", null));
            } else if (record.version() != selection.expectedVersion()) {
                failures.add(RecordRuntimeViews.BatchMutationItem.rejected(
                        selection.externalRecordId(), "VERSION_STALE", record.version()));
            } else if (!allowedStates.contains(record.status())
                    || additionalInvalidStateIds.contains(record.recordId())) {
                failures.add(RecordRuntimeViews.BatchMutationItem.rejected(
                        selection.externalRecordId(), "STATE_INVALID", record.version()));
            }
        }
        return List.copyOf(failures);
    }

    private List<RecordRuntimeViews.BatchMutationItem> successItems(
            Map<Long, Long> newVersions,
            String targetStatus
    ) {
        var items = new ArrayList<RecordRuntimeViews.BatchMutationItem>(requestOrder.size());
        for (var selection : requestOrder) {
            items.add(RecordRuntimeViews.BatchMutationItem.applied(
                    selection.externalRecordId(), newVersions.get(selection.recordId()), targetStatus));
        }
        return List.copyOf(items);
    }

    private static BusinessException invalid(String operation, long minimumVersion) {
        var name = operation == null || operation.isBlank() ? "mutation" : operation.trim();
        if ("archive".equals(name) && minimumVersion == 1) {
            return new BusinessException(
                    "BATCH_INVALID",
                    "Batch archive requires 1 to 200 unique positive record IDs and positive expected versions",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var versionRule = minimumVersion == 0 ? "non-negative" : "at least " + minimumVersion;
        return new BusinessException(
                "BATCH_INVALID",
                "Batch " + name + " requires 1 to 200 unique positive record IDs and "
                        + versionRule + " expected versions",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private String preconditionMessage() {
        return switch (operation) {
            case "archive" -> "No records were archived because one or more batch preconditions failed";
            case "trash" -> "No records were moved to trash because one or more batch preconditions failed";
            default -> "Batch " + operation + " was not applied because one or more preconditions failed";
        };
    }

    record Selection(String externalRecordId, long recordId, long expectedVersion) { }

    record RecordState(long recordId, long version, String status) { }

    final class PreparedBatch {
        private PreparedBatch() {
        }

        RecordRuntimeViews.BatchMutationResponse apply(
                String targetStatus,
                LongFunction<Long> transition
        ) {
            if (targetStatus == null || targetStatus.isBlank()) {
                throw new IllegalArgumentException("Batch mutation target status is required");
            }
            var newVersions = new HashMap<Long, Long>();
            for (var recordId : lockOrder) {
                newVersions.put(recordId, transition.apply(recordId));
            }
            return new RecordRuntimeViews.BatchMutationResponse(
                    true,
                    successItems(newVersions, targetStatus));
        }
    }
}
