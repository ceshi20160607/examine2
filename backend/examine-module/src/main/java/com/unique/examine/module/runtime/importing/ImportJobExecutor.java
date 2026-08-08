package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.notification.JobResultNotifier;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportJobExecutor {
    private final ImportRepository repository;
    private final RecordRuntimeService records;
    private final DurableJobFacade jobs;
    private final OperationAuditFacade audit;
    private final ObjectMapper mapper;
    private final JobResultNotifier notifier;

    public ImportJobExecutor(ImportRepository repository, RecordRuntimeService records, DurableJobFacade jobs,
                             OperationAuditFacade audit, ObjectMapper mapper, JobResultNotifier notifier) {
        this.repository = repository;
        this.records = records;
        this.jobs = jobs;
        this.audit = audit;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @Transactional
    public void execute(DurableJobFacade.JobRecord job) {
        switch (job.jobType()) {
            case ImportService.PREVIEW_JOB -> preview(job);
            case ImportService.COMMIT_JOB -> commit(job);
            case ImportService.ROLLBACK_JOB -> rollback(job);
            default -> throw new IllegalArgumentException("Unsupported import job " + job.jobType());
        }
    }

    @Transactional
    public void markTerminalFailure(DurableJobFacade.JobRecord job, RuntimeException failure) {
        var batchId = batchId(job);
        var status = switch (job.jobType()) {
            case ImportService.PREVIEW_JOB -> "PREVIEW_QUEUED";
            case ImportService.COMMIT_JOB -> "COMMIT_QUEUED";
            case ImportService.ROLLBACK_JOB -> "ROLLBACK_QUEUED";
            default -> null;
        };
        if (status != null) {
            var batch = repository.require(batchId);
            repository.markFailed(batchId, status);
            if (ImportService.COMMIT_JOB.equals(job.jobType())) notifier.importFailed(batch, failure.getMessage());
        }
    }

    private void preview(DurableJobFacade.JobRecord job) {
        var batch = repository.require(batchId(job));
        repository.transition(batch.id(), "PREVIEW_QUEUED", "PREVIEWING");
        var session = session(job);
        var pending = repository.rows(batch.id());
        var planned = new ArrayList<PlannedRow>();
        for (var row : pending) {
            try {
                var probe = records.probeImportRow(session, batch.moduleCode(), Long.toString(batch.schemaVersionId()),
                        batch.mode(), batch.matchFieldCode(), row.input());
                planned.add(PlannedRow.valid(row, probe));
            } catch (BusinessException exception) {
                planned.add(PlannedRow.invalid(row, exception.code(), exception.getMessage()));
            }
        }
        var occurrences = new HashMap<String, List<PlannedRow>>();
        planned.stream().filter(PlannedRow::valid).forEach(row -> row.probe().fingerprints().forEach(fingerprint ->
                occurrences.computeIfAbsent(key(fingerprint), ignored -> new ArrayList<>()).add(row)));
        occurrences.values().stream().filter(rows -> rows.size() > 1).forEach(rows -> rows.forEach(row ->
                row.invalidate("IMPORT_DUPLICATE_IN_REQUEST", "Unique value is duplicated inside this import")));

        var newRows = 0;
        var updateRows = 0;
        var failedRows = 0;
        for (var row : planned) {
            if (row.valid()) {
                if ("NEW".equals(row.probe().action())) newRows++; else updateRows++;
                repository.savePreview(new ImportRepository.RowPreview(row.row().id(), row.probe().action(), "VALID",
                        row.probe().fingerprints(), null, null, row.probe().targetRecordId(),
                        "UPDATE".equals(row.probe().action()) ? row.probe().targetVersion() : null));
            } else {
                failedRows++;
                repository.savePreview(new ImportRepository.RowPreview(row.row().id(), null, "INVALID", List.of(),
                        row.errorCode(), row.errorMessage(), null, null));
            }
        }
        repository.finishPreview(batch.id(), newRows, updateRows, failedRows);
        jobs.succeed(job.id(), job.version(), Map.of("batchId", Long.toString(batch.id()), "newRows", newRows,
                "updateRows", updateRows, "failedRows", failedRows));
        changed(session, batch, "MODULE_IMPORT_PREVIEWED",
                Map.of("status", failedRows == 0 ? "READY" : "INVALID", "newRows", newRows,
                        "updateRows", updateRows, "failedRows", failedRows));
    }

    private void commit(DurableJobFacade.JobRecord job) {
        var batch = repository.require(batchId(job));
        repository.transition(batch.id(), "COMMIT_QUEUED", "COMMITTING");
        var session = session(job);
        var schema = records.importSchema(session, batch.moduleCode());
        requireSchema(batch, schema);
        var rows = repository.rows(batch.id());
        if (rows.stream().anyMatch(row -> !"VALID".equals(row.status()))) {
            throw conflict("IMPORT_NOT_READY", "All rows must be valid before commit");
        }

        for (var row : rows) {
            var probe = records.probeImportRow(session, batch.moduleCode(), Long.toString(batch.schemaVersionId()),
                    batch.mode(), batch.matchFieldCode(), row.input());
            if (!probe.action().equals(row.action()) || !java.util.Objects.equals(probe.targetRecordId(), row.targetRecordId())
                    || "UPDATE".equals(probe.action()) && probe.targetVersion() != currentTargetVersion(row)) {
                throw conflict("IMPORT_PREVIEW_STALE", "Record state changed after import preview");
            }
        }

        var completedRecords = new ArrayList<CompletedRecord>();
        for (var row : rows) {
            if ("NEW".equals(row.action())) {
                var created = records.create(session, batch.moduleCode(), new RecordRuntimeViews.CreateRecordRequest(
                                Long.toString(batch.schemaVersionId()), null, row.input(), List.of(), List.of()),
                        key(batch.id(), row.rowNumber(), "create"), batch.requestId(), batch.traceId());
                var active = records.activate(session, batch.moduleCode(), Long.parseLong(created.recordId()),
                        new RecordRuntimeViews.VersionCommandRequest(created.version()),
                        key(batch.id(), row.rowNumber(), "activate"), batch.requestId(), batch.traceId());
                repository.saveCommittedRow(row.id(), Long.parseLong(active.recordId()), null, active.version());
                completedRecords.add(new CompletedRecord(Long.parseLong(active.recordId()), active.version()));
            } else {
                var touched = Set.copyOf(row.input().keySet());
                var before = records.importSnapshot(session, batch.moduleCode(), row.targetRecordId(), touched);
                var response = records.batchEdit(session, batch.moduleCode(),
                        new RecordRuntimeViews.BatchEditRequest(
                                List.of(new RecordRuntimeViews.BatchRecordRef(Long.toString(row.targetRecordId()),
                                        before.version())), changes(row.input())),
                        key(batch.id(), row.rowNumber(), "update"), batch.requestId(), batch.traceId());
                var nextVersion = response.items().getFirst().newVersion();
                repository.saveCommittedRow(row.id(), row.targetRecordId(), snapshot(before, touched), nextVersion);
                completedRecords.add(new CompletedRecord(row.targetRecordId(), nextVersion));
            }
        }
        completedRecords.forEach(record -> records.publishImportCompleted(
                session, batch.moduleCode(), record.recordId(), record.version()));
        repository.finishCommit(batch.id());
        jobs.succeed(job.id(), job.version(), Map.of("batchId", Long.toString(batch.id()),
                "newRows", batch.newRows(), "updateRows", batch.updateRows(), "failedRows", 0));
        changed(session, batch, "MODULE_IMPORT_COMMITTED", Map.of("status", "COMMITTED",
                "newRows", batch.newRows(), "updateRows", batch.updateRows()));
        notifier.importSucceeded(batch);
    }

    private void rollback(DurableJobFacade.JobRecord job) {
        var batch = repository.require(batchId(job));
        repository.transition(batch.id(), "ROLLBACK_QUEUED", "ROLLING_BACK");
        var session = session(job);
        var rows = repository.rows(batch.id());
        if (rows.stream().anyMatch(row -> !"COMMITTED".equals(row.status()) || row.targetRecordId() == null
                || row.targetAfterVersion() == null)) {
            throw conflict("IMPORT_NOT_ROLLBACKABLE", "Committed row evidence is incomplete");
        }

        var current = new LinkedHashMap<Long, RecordRuntimeService.ImportRecordSnapshot>();
        for (var row : rows) {
            var touched = "UPDATE".equals(row.action()) ? touched(row.before()) : Set.<String>of();
            var snapshot = records.importSnapshot(session, batch.moduleCode(), row.targetRecordId(), touched);
            if (snapshot.version() != row.targetAfterVersion()) {
                throw conflict("IMPORT_ROLLBACK_VERSION_CONFLICT",
                        "A record changed after import; rollback cannot be applied safely");
            }
            current.put(row.id(), snapshot);
        }

        for (var row : rows) {
            var snapshot = current.get(row.id());
            final long nextVersion;
            if ("NEW".equals(row.action())) {
                var trashed = records.trash(session, batch.moduleCode(), row.targetRecordId(),
                        new RecordRuntimeViews.VersionCommandRequest(snapshot.version()),
                        key(batch.id(), row.rowNumber(), "rollback-trash"), batch.requestId(), batch.traceId());
                nextVersion = trashed.version();
            } else {
                var response = records.batchEdit(session, batch.moduleCode(),
                        new RecordRuntimeViews.BatchEditRequest(
                                List.of(new RecordRuntimeViews.BatchRecordRef(Long.toString(row.targetRecordId()),
                                        snapshot.version())), restoreChanges(row.before())),
                        key(batch.id(), row.rowNumber(), "rollback-update"), batch.requestId(), batch.traceId());
                nextVersion = response.items().getFirst().newVersion();
            }
            repository.saveRolledBackRow(row.id(), nextVersion);
        }
        repository.finishRollback(batch.id());
        jobs.succeed(job.id(), job.version(), Map.of("batchId", Long.toString(batch.id()), "status", "ROLLED_BACK"));
        changed(session, batch, "MODULE_IMPORT_ROLLED_BACK", Map.of("status", "ROLLED_BACK"));
    }

    private void changed(RuntimeSession session, ImportRepository.BatchRecord batch, String action, Object after) {
        audit.recordSuccess(OperationAudit.success(new OperationAudit.Actor(session.accountId(), "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("MODULE_IMPORT_BATCH", Long.toString(batch.id())), action, null, after,
                batch.requestId(), batch.traceId()));
    }

    private ObjectNode snapshot(RecordRuntimeService.ImportRecordSnapshot before, Set<String> touched) {
        var node = mapper.createObjectNode();
        node.put("version", before.version());
        var touchedNode = node.putArray("touchedFields");
        touched.forEach(touchedNode::add);
        var values = node.putObject("values");
        before.values().forEach(values::set);
        return node;
    }

    private List<RecordRuntimeViews.BatchFieldChange> restoreChanges(JsonNode snapshot) {
        var values = snapshot.path("values");
        var result = new ArrayList<RecordRuntimeViews.BatchFieldChange>();
        snapshot.path("touchedFields").forEach(field -> {
            var code = field.asText();
            result.add(values.has(code)
                    ? new RecordRuntimeViews.BatchFieldChange(code, "SET", values.get(code))
                    : new RecordRuntimeViews.BatchFieldChange(code, "CLEAR", null));
        });
        return List.copyOf(result);
    }

    private static Set<String> touched(JsonNode snapshot) {
        if (snapshot == null || !snapshot.has("touchedFields")) return Set.of();
        return java.util.stream.StreamSupport.stream(snapshot.path("touchedFields").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toUnmodifiableSet());
    }

    private static List<RecordRuntimeViews.BatchFieldChange> changes(Map<String, JsonNode> values) {
        return values.entrySet().stream().map(entry -> {
            var value = entry.getValue();
            var clear = value == null || value.isNull() || value.isTextual() && value.asText().isBlank()
                    || value.isArray() && value.isEmpty();
            return new RecordRuntimeViews.BatchFieldChange(entry.getKey(), clear ? "CLEAR" : "SET",
                    clear ? null : value);
        }).toList();
    }

    private RuntimeSession session(DurableJobFacade.JobRecord job) {
        var input = job.input();
        @SuppressWarnings("unchecked")
        var permissionValues = (Collection<Object>) input.getOrDefault("permissions", List.of());
        var permissions = permissionValues.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        return new RuntimeSession(number(input, "accountId"), number(input, "systemId"),
                number(input, "memberId"), number(input, "tenantId"), permissions);
    }

    private static long batchId(DurableJobFacade.JobRecord job) { return number(job.input(), "batchId"); }
    private static long number(Map<String, Object> input, String key) {
        try { return Long.parseLong(String.valueOf(input.get(key))); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Missing job input " + key, exception); }
    }
    private static long currentTargetVersion(ImportRepository.RowRecord row) {
        return row.targetRecordId() == null ? -1 : row.targetAfterVersion() == null ? -1 : row.targetAfterVersion();
    }
    private static void requireSchema(ImportRepository.BatchRecord batch, RecordRuntimeViews.RecordSchema schema) {
        if (batch.schemaVersionId() != Long.parseLong(schema.schemaVersionId())
                || batch.moduleSnapshotId() != Long.parseLong(schema.moduleSnapshotId())
                || !batch.schemaChecksum().equals(schema.checksum())) {
            throw conflict("IMPORT_SCHEMA_STALE", "Published module schema changed after preview");
        }
    }
    private static String key(long batchId, int row, String action) {
        return "import:" + batchId + ":" + row + ":" + action;
    }
    private static String key(RecordRuntimeService.ImportUniqueFingerprint value) {
        return value.fieldCode() + "|" + value.currency() + "|" + value.hash() + "|" + value.hashKeyVersion();
    }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private static final class PlannedRow {
        private final ImportRepository.RowRecord row;
        private final RecordRuntimeService.ImportRowProbe probe;
        private String errorCode;
        private String errorMessage;

        private PlannedRow(ImportRepository.RowRecord row, RecordRuntimeService.ImportRowProbe probe,
                           String errorCode, String errorMessage) {
            this.row = row; this.probe = probe; this.errorCode = errorCode; this.errorMessage = errorMessage;
        }
        static PlannedRow valid(ImportRepository.RowRecord row, RecordRuntimeService.ImportRowProbe probe) {
            return new PlannedRow(row, probe, null, null);
        }
        static PlannedRow invalid(ImportRepository.RowRecord row, String code, String message) {
            return new PlannedRow(row, null, code, message);
        }
        void invalidate(String code, String message) { this.errorCode = code; this.errorMessage = message; }
        boolean valid() { return errorCode == null; }
        ImportRepository.RowRecord row() { return row; }
        RecordRuntimeService.ImportRowProbe probe() { return probe; }
        String errorCode() { return errorCode; }
        String errorMessage() { return errorMessage; }
    }

    private record CompletedRecord(long recordId, long version) { }
}
