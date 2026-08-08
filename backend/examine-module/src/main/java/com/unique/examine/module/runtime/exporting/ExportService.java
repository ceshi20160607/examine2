package com.unique.examine.module.runtime.exporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.runtime.query.RecordQueryParser;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExportService {
    public static final String JOB_TYPE = "MODULE_XLSX_EXPORT";
    public static final int MAX_ROWS = 5_000;
    private static final Set<String> COMPOSITION_TYPES = Set.of("RELATION", "REFERENCE", "SUBTABLE");

    private final RecordRuntimeService records;
    private final ExportRepository repository;
    private final DurableJobFacade jobs;
    private final IdService ids;
    private final ObjectMapper mapper;
    private final RecordQueryParser queries;

    public ExportService(RecordRuntimeService records, ExportRepository repository, DurableJobFacade jobs,
                         IdService ids, ObjectMapper mapper, RecordQueryParser queries) {
        this.records = records;
        this.repository = repository;
        this.jobs = jobs;
        this.ids = ids;
        this.mapper = mapper;
        this.queries = queries;
    }

    @Transactional
    public ExportViews.Task create(RuntimeSession session, String moduleCode, ExportViews.CreateRequest request,
                                   String requestId, String traceId) {
        if (request == null || request.query() == null || !request.query().isObject()
                || request.fieldCodes().isEmpty() || request.fieldCodes().size() > 100
                || new LinkedHashSet<>(request.fieldCodes()).size() != request.fieldCodes().size()) {
            throw invalid("EXPORT_REQUEST_INVALID",
                    "Export requires one query and 1..100 unique readable field codes");
        }
        var schema = records.exportSchema(session, moduleCode);
        var available = new LinkedHashMap<String, com.unique.examine.module.runtime.api.RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> available.put(field.fieldCode(), field));
        for (var code : request.fieldCodes()) {
            var field = available.get(code);
            if (field == null || !field.readable() || field.masked() || COMPOSITION_TYPES.contains(field.type())) {
                throw invalid("EXPORT_FIELD_FORBIDDEN", "Export field is unavailable or protected: " + code);
            }
        }

        var root = ((ObjectNode) request.query()).deepCopy();
        root.put("page", 1);
        root.put("size", 200);
        root.putNull("viewId");
        ArrayNode columns = root.putArray("columns");
        request.fieldCodes().forEach(columns::add);
        var parsed = queries.parse(write(root));
        records.validateQuery(session, moduleCode, parsed.canonicalJson());

        var exportId = ids.nextId();
        var input = Map.<String, Object>of(
                "exportId", Long.toString(exportId),
                "accountId", Long.toString(session.accountId()),
                "memberId", Long.toString(session.memberId()),
                "systemId", Long.toString(session.systemId()),
                "tenantId", Long.toString(requiredTenant(session)),
                "permissions", session.permissions().stream().sorted().toList());
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(JOB_TYPE, "MODULE_EXPORT_TASK",
                Long.toString(exportId), session.systemId(), requiredTenant(session), session.memberId(), input, 3));
        var now = LocalDateTime.now();
        repository.insert(new ExportRepository.TaskRecord(exportId, session.systemId(), requiredTenant(session),
                Long.parseLong(schema.logicalModuleId()), moduleCode, Long.parseLong(schema.schemaVersionId()),
                Long.parseLong(schema.moduleSnapshotId()), schema.checksum(), parsed.canonicalJson(),
                queries.sha256(parsed.canonicalJson()), request.fieldCodes(), session.permissions(), "QUEUED",
                null, 0, null, null, null, null, job.id(), session.accountId(), session.memberId(), requestId,
                traceId, null, null, now, now, 0));
        return view(repository.require(exportId));
    }

    public ExportViews.Task get(RuntimeSession session, String moduleCode, long exportId) {
        var task = owned(session, moduleCode, exportId);
        records.exportSchema(session, moduleCode);
        return view(task);
    }

    public ExportViews.TaskPage list(RuntimeSession session, String moduleCode, int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw invalid("EXPORT_PAGE_INVALID", "Export history page must be >=1 and size must be 1..50");
        }
        records.exportSchema(session, moduleCode);
        var tenantId = requiredTenant(session);
        var total = repository.countOwned(session.systemId(), tenantId, moduleCode, session.memberId());
        var offset = Math.multiplyExact((long) page - 1L, size);
        var items = repository.pageOwned(session.systemId(), tenantId, moduleCode, session.memberId(), size, offset)
                .stream().map(this::view).toList();
        return new ExportViews.TaskPage(items, page, size, total);
    }

    public Result result(RuntimeSession session, String moduleCode, long exportId) {
        var task = owned(session, moduleCode, exportId);
        records.exportSchema(session, moduleCode);
        if (!"SUCCEEDED".equals(task.status())) {
            throw new BusinessException("EXPORT_RESULT_NOT_READY", "Export result is not ready", HttpStatus.CONFLICT);
        }
        return new Result(task.resultFilename(), repository.result(task.id()));
    }

    ExportViews.Task view(ExportRepository.TaskRecord task) {
        return new ExportViews.Task(Long.toString(task.id()), task.moduleCode(),
                Long.toString(task.schemaVersionId()), task.queryHash(), task.fieldCodes(), task.status(),
                task.totalRows(), task.processedRows(), Long.toString(task.jobId()), task.resultFilename(),
                task.resultSize(), task.failureCode(), task.failureMessage(), task.createdAt(), task.startedAt(),
                task.finishedAt());
    }

    private ExportRepository.TaskRecord owned(RuntimeSession session, String moduleCode, long exportId) {
        var task = repository.require(exportId);
        if (task.systemId() != session.systemId() || task.tenantId() != requiredTenant(session)
                || task.memberId() != session.memberId() || !task.moduleCode().equals(moduleCode)) {
            throw new BusinessException("EXPORT_NOT_FOUND", "Export task was not found", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw invalid("EXPORT_QUERY_INVALID", "Export query cannot be serialized"); }
    }
    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }
    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record Result(String filename, byte[] content) { }
}
