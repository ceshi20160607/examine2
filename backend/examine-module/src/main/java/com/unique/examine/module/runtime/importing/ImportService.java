package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ImportService {
    static final String PREVIEW_JOB = "MODULE_IMPORT_PREVIEW";
    static final String COMMIT_JOB = "MODULE_IMPORT_COMMIT";
    static final String ROLLBACK_JOB = "MODULE_IMPORT_ROLLBACK";

    private static final Set<String> EXCLUDED_TYPES = Set.of(
            "IDENTITY", "SECRET", "RELATION", "REFERENCE", "SUBTABLE",
            "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE",
            "FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE",
            "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");

    private final RecordRuntimeService records;
    private final ImportRepository repository;
    private final DurableJobFacade jobs;
    private final IdService ids;
    private final ObjectMapper mapper;
    private final ImportXlsxCodec xlsx;

    public ImportService(RecordRuntimeService records, ImportRepository repository, DurableJobFacade jobs,
                         IdService ids, ObjectMapper mapper, ImportXlsxCodec xlsx) {
        this.records = records;
        this.repository = repository;
        this.jobs = jobs;
        this.ids = ids;
        this.mapper = mapper;
        this.xlsx = xlsx;
    }

    public ImportViews.Template template(RuntimeSession session, String moduleCode) {
        var schema = records.importSchema(session, moduleCode);
        var fields = new ArrayList<ImportViews.TemplateField>();
        var excluded = new ArrayList<ImportViews.ExcludedField>();
        for (var field : schema.fields()) {
            if (field.writable() && !EXCLUDED_TYPES.contains(field.type())) {
                fields.add(new ImportViews.TemplateField(field.fieldCode(), field.fieldName(), field.type(),
                        field.schema().path("required").asBoolean(false),
                        field.schema().path("unique").asBoolean(false)));
            } else {
                excluded.add(new ImportViews.ExcludedField(field.fieldCode(), field.fieldName(), field.type(),
                        exclusionReason(field)));
            }
        }
        return new ImportViews.Template(schema.schemaVersionId(), schema.moduleSnapshotId(), schema.checksum(),
                List.copyOf(fields), List.copyOf(excluded), List.of("NEW", "UPSERT"), 200);
    }

    @Transactional
    public ImportViews.Batch preview(RuntimeSession session, String moduleCode, ImportViews.PreviewRequest request,
                                     String requestId, String traceId) {
        validate(request);
        var template = template(session, moduleCode);
        if ("UPSERT".equals(request.mode())) {
            var validMatch = template.fields().stream().anyMatch(field -> field.unique()
                    && field.fieldCode().equals(request.matchFieldCode()));
            if (!validMatch) throw invalid("IMPORT_MATCH_FIELD_INVALID",
                    "UPSERT requires one writable UNIQUE field from the current template");
        }
        var allowed = template.fields().stream().map(ImportViews.TemplateField::fieldCode)
                .collect(java.util.stream.Collectors.toSet());
        for (var row : request.rows()) {
            if (row == null || row.isEmpty() || !allowed.containsAll(row.keySet())) {
                throw invalid("IMPORT_ROW_INVALID", "Each row must use only fields from the current import template");
            }
        }
        var batchId = ids.nextId();
        var now = LocalDateTime.now();
        var input = jobInput(batchId, session);
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(PREVIEW_JOB, "MODULE_IMPORT_BATCH",
                Long.toString(batchId), session.systemId(), requiredTenant(session), session.memberId(), input, 3));
        var batch = new ImportRepository.BatchRecord(batchId, session.systemId(), requiredTenant(session),
                Long.parseLong(template.moduleSnapshotId()), moduleCode, Long.parseLong(template.schemaVersionId()),
                Long.parseLong(template.moduleSnapshotId()), template.checksum(), request.mode(),
                blank(request.matchFieldCode()), hash(request), "PREVIEW_QUEUED", request.rows().size(), 0, 0, 0,
                job.id(), null, null, session.accountId(), session.memberId(), requestId, traceId, now, now);
        var rowRecords = new ArrayList<ImportRepository.RowRecord>();
        for (var index = 0; index < request.rows().size(); index++) {
            rowRecords.add(new ImportRepository.RowRecord(ids.nextId(), batchId, session.systemId(),
                    requiredTenant(session), index + 1, null, "PREVIEW_PENDING",
                    Map.copyOf(request.rows().get(index)), null, null, null, null, null, null, now, now));
        }
        repository.insert(batch, rowRecords);
        return view(repository.require(batchId));
    }

    public ImportViews.Batch get(RuntimeSession session, String moduleCode, long batchId) {
        var batch = owned(session, moduleCode, batchId);
        records.importSchema(session, moduleCode);
        return view(batch);
    }

    public byte[] templateXlsx(RuntimeSession session, String moduleCode) {
        return xlsx.template(template(session, moduleCode));
    }

    @Transactional
    public ImportViews.Batch previewXlsx(RuntimeSession session, String moduleCode, String mode,
                                         String matchFieldCode, String filename, byte[] content,
                                         String requestId, String traceId) {
        var currentTemplate = template(session, moduleCode);
        try {
            var rows = xlsx.parse(content, filename, currentTemplate);
            return preview(session, moduleCode, new ImportViews.PreviewRequest(mode, matchFieldCode, rows),
                    requestId, traceId);
        } catch (ImportXlsxCodec.ImportWorkbookException exception) {
            throw invalid(exception.code(), exception.getMessage());
        }
    }

    public ImportViews.BatchPage list(RuntimeSession session, String moduleCode, int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw invalid("IMPORT_PAGE_INVALID", "Import history page must be >=1 and size must be 1..50");
        }
        records.importSchema(session, moduleCode);
        var tenantId = requiredTenant(session);
        var total = repository.countOwned(session.systemId(), tenantId, moduleCode, session.memberId());
        var offset = Math.multiplyExact((long) page - 1L, size);
        var items = repository.pageOwned(session.systemId(), tenantId, moduleCode, session.memberId(), size, offset)
                .stream().map(this::view).toList();
        return new ImportViews.BatchPage(items, page, size, total);
    }

    public byte[] errorsXlsx(RuntimeSession session, String moduleCode, long batchId) {
        var batch = owned(session, moduleCode, batchId);
        records.importSchema(session, moduleCode);
        var failed = repository.rows(batch.id()).stream()
                .filter(row -> "INVALID".equals(row.status()))
                .toList();
        if (failed.isEmpty()) {
            throw new BusinessException("IMPORT_ERRORS_NOT_AVAILABLE",
                    "Import batch has no failed preview rows", HttpStatus.CONFLICT);
        }
        return xlsx.errors(failed);
    }

    @Transactional
    public ImportViews.Batch commit(RuntimeSession session, String moduleCode, long batchId) {
        var batch = owned(session, moduleCode, batchId);
        var schema = records.importSchema(session, moduleCode);
        requireSchema(batch, schema);
        if (batch.commitJobId() != null) return view(batch);
        if (!"READY".equals(batch.status())) {
            throw new BusinessException("IMPORT_NOT_READY", "Import preview is not fully valid and ready",
                    HttpStatus.CONFLICT);
        }
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(COMMIT_JOB, "MODULE_IMPORT_BATCH",
                Long.toString(batchId), session.systemId(), requiredTenant(session), session.memberId(),
                jobInput(batchId, session), 3));
        if (!repository.attachJob(batchId, "READY", "COMMIT_QUEUED", "commit_job_id", job.id())) {
            throw new BusinessException("IMPORT_BATCH_CONFLICT", "Import batch state changed", HttpStatus.CONFLICT);
        }
        return view(repository.require(batchId));
    }

    @Transactional
    public ImportViews.Batch rollback(RuntimeSession session, String moduleCode, long batchId) {
        var batch = owned(session, moduleCode, batchId);
        records.importSchema(session, moduleCode);
        if (batch.rollbackJobId() != null) return view(batch);
        if (!"COMMITTED".equals(batch.status())) {
            throw new BusinessException("IMPORT_NOT_ROLLBACKABLE", "Only a committed import can be rolled back",
                    HttpStatus.CONFLICT);
        }
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(ROLLBACK_JOB, "MODULE_IMPORT_BATCH",
                Long.toString(batchId), session.systemId(), requiredTenant(session), session.memberId(),
                jobInput(batchId, session), 3));
        if (!repository.attachJob(batchId, "COMMITTED", "ROLLBACK_QUEUED", "rollback_job_id", job.id())) {
            throw new BusinessException("IMPORT_BATCH_CONFLICT", "Import batch state changed", HttpStatus.CONFLICT);
        }
        return view(repository.require(batchId));
    }

    ImportViews.Batch view(ImportRepository.BatchRecord batch) {
        var rowViews = repository.rows(batch.id()).stream().map(row -> new ImportViews.Row(
                row.rowNumber(), row.action(), row.status(), row.errorCode(), row.errorMessage(),
                row.targetRecordId() == null ? null : Long.toString(row.targetRecordId()), row.targetAfterVersion()
        )).toList();
        return new ImportViews.Batch(Long.toString(batch.id()), batch.moduleCode(),
                Long.toString(batch.schemaVersionId()), batch.mode(), batch.matchFieldCode(), batch.status(),
                batch.totalRows(), batch.newRows(), batch.updateRows(), batch.failedRows(),
                Long.toString(batch.previewJobId()), string(batch.commitJobId()), string(batch.rollbackJobId()), rowViews);
    }

    private ImportRepository.BatchRecord owned(RuntimeSession session, String moduleCode, long batchId) {
        var batch = repository.require(batchId);
        if (batch.systemId() != session.systemId() || batch.tenantId() != requiredTenant(session)
                || batch.memberId() != session.memberId() || !batch.moduleCode().equals(moduleCode)) {
            throw new BusinessException("IMPORT_BATCH_NOT_FOUND", "Import batch was not found", HttpStatus.NOT_FOUND);
        }
        return batch;
    }

    private static void validate(ImportViews.PreviewRequest request) {
        if (request == null || !Set.of("NEW", "UPSERT").contains(request.mode())
                || request.rows().isEmpty() || request.rows().size() > 200) {
            throw invalid("IMPORT_REQUEST_INVALID", "Import requires NEW/UPSERT mode and 1..200 rows");
        }
    }

    private static void requireSchema(ImportRepository.BatchRecord batch, RecordRuntimeViews.RecordSchema schema) {
        if (batch.schemaVersionId() != Long.parseLong(schema.schemaVersionId())
                || batch.moduleSnapshotId() != Long.parseLong(schema.moduleSnapshotId())
                || !batch.schemaChecksum().equals(schema.checksum())) {
            throw new BusinessException("IMPORT_SCHEMA_STALE", "Published module schema changed after preview",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> jobInput(long batchId, RuntimeSession session) {
        return Map.of("batchId", Long.toString(batchId), "accountId", Long.toString(session.accountId()),
                "memberId", Long.toString(session.memberId()), "systemId", Long.toString(session.systemId()),
                "tenantId", Long.toString(requiredTenant(session)), "permissions", List.copyOf(session.permissions()));
    }

    private String hash(Object value) {
        try {
            var bytes = mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Import request is not serializable", exception);
        }
    }

    private static String exclusionReason(RecordRuntimeViews.FieldCapability field) {
        if (!field.writable()) return "READONLY";
        if (Set.of("IDENTITY", "SECRET").contains(field.type())) return "SENSITIVE";
        if (Set.of("ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE").contains(field.type())) return "FILE_OWNED";
        if (Set.of("RELATION", "REFERENCE", "SUBTABLE").contains(field.type())) return "COMPOSITION_OWNED";
        if (Set.of("FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE").contains(field.type())) {
            return "DERIVED";
        }
        return "SYSTEM_OWNED";
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String string(Long value) { return value == null ? null : Long.toString(value); }
}
