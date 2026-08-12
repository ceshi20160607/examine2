package com.unique.examine.module.runtime.exporting;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExportJobExecutor {
    private final ExportRepository repository;
    private final RecordRuntimeService records;
    private final ExportXlsxCodec xlsx;
    private final DurableJobFacade jobs;
    private final OperationAuditFacade audit;
    private final ObjectMapper mapper;
    private final JobResultNotifier notifier;

    public ExportJobExecutor(ExportRepository repository, RecordRuntimeService records, ExportXlsxCodec xlsx,
                             DurableJobFacade jobs, OperationAuditFacade audit, ObjectMapper mapper,
                             JobResultNotifier notifier) {
        this.repository = repository;
        this.records = records;
        this.xlsx = xlsx;
        this.jobs = jobs;
        this.audit = audit;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @Transactional
    public void execute(DurableJobFacade.JobRecord job) {
        if (!ExportService.JOB_TYPE.equals(job.jobType())) {
            throw new IllegalArgumentException("Unsupported export job " + job.jobType());
        }
        var task = repository.require(exportId(job));
        repository.start(task.id());
        var session = session(job);
        var schema = records.exportSchema(session, task.moduleCode());
        requireSchema(task, schema);
        var fields = new LinkedHashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> fields.put(field.fieldCode(), field));
        var columns = task.fieldCodes().stream().map(code -> {
            var field = fields.get(code);
            if (field == null || !field.readable() || field.masked()) {
                throw new BusinessException("EXPORT_SCHEMA_STALE", "Export field authorization changed",
                        HttpStatus.CONFLICT);
            }
            return new ExportXlsxCodec.Column(code, field.fieldName(), field.type());
        }).toList();

        var outputRows = new ArrayList<RecordRuntimeViews.RecordSummary>();
        long total = -1L;
        for (var page = 1; ; page++) {
            var result = records.query(session, task.moduleCode(), page(task.queryJson(), page));
            if (page == 1) {
                total = result.total();
                if (total > ExportService.MAX_ROWS) {
                    throw new BusinessException("EXPORT_ROW_LIMIT_EXCEEDED",
                            "Export contains more than " + ExportService.MAX_ROWS + " records",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                repository.total(task.id(), Math.toIntExact(total));
            }
            outputRows.addAll(result.rows());
            if (result.rows().isEmpty() || outputRows.size() >= total) break;
        }
        var filename = task.moduleCode() + "-export-" + task.id() + ".xlsx";
        var content = xlsx.write(task.moduleCode(), columns, outputRows);
        repository.complete(task.id(), outputRows.size(), filename, content);
        jobs.succeed(job.id(), job.version(), java.util.Map.of("exportId", Long.toString(task.id()),
                "rows", outputRows.size(), "bytes", content.length));
        audit.recordSuccess(OperationAudit.success(new OperationAudit.Actor(session.accountId(), "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("MODULE_EXPORT_TASK", Long.toString(task.id())), "MODULE_XLSX_EXPORTED",
                null, java.util.Map.of("status", "SUCCEEDED", "rows", outputRows.size()),
                task.requestId(), task.traceId()));
        notifier.exportSucceeded(task, outputRows.size());
    }

    @Transactional
    public void markTerminalFailure(DurableJobFacade.JobRecord job, RuntimeException failure) {
        var task = repository.require(exportId(job));
        var code = failure instanceof BusinessException business ? business.code() : "EXPORT_FAILED";
        var message = failure instanceof BusinessException ? failure.getMessage() : "Export task failed safely";
        repository.fail(task.id(), code, message);
        notifier.exportFailed(task, message);
    }

    private String page(String queryJson, int page) {
        try {
            var root = (ObjectNode) mapper.readTree(queryJson);
            root.put("page", page);
            root.put("size", 200);
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored export query is invalid", exception);
        }
    }

    private static void requireSchema(ExportRepository.TaskRecord task, RecordRuntimeViews.RecordSchema schema) {
        if (task.schemaVersionId() != Long.parseLong(schema.schemaVersionId())
                || task.moduleSnapshotId() != Long.parseLong(schema.moduleSnapshotId())
                || !task.schemaChecksum().equals(schema.checksum())) {
            throw new BusinessException("EXPORT_SCHEMA_STALE", "Published module schema changed after enqueue",
                    HttpStatus.CONFLICT);
        }
    }

    private RuntimeSession session(DurableJobFacade.JobRecord job) {
        var input = job.input();
        @SuppressWarnings("unchecked")
        var permissionValues = (Collection<Object>) input.getOrDefault("permissions", List.of());
        var permissions = permissionValues.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        return new RuntimeSession(number(input, "accountId"), number(input, "systemId"),
                number(input, "memberId"), number(input, "tenantId"), permissions);
    }
    private static long exportId(DurableJobFacade.JobRecord job) { return number(job.input(), "exportId"); }
    private static long number(java.util.Map<String, Object> input, String key) {
        try { return Long.parseLong(String.valueOf(input.get(key))); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Missing job input " + key, exception); }
    }
}
