package com.unique.examine.module.runtime.printing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrintService {
    public static final String JOB_TYPE = "MODULE_RECORD_PDF_PRINT";
    private static final Set<String> FORBIDDEN_TYPES = Set.of("IDENTITY", "SECRET");
    private static final int MAX_COMPOSITION_ROWS = 500;

    private final RecordRuntimeService records;
    private final PrintRepository repository;
    private final PrintRenderer renderer;
    private final DurableJobFacade jobs;
    private final RecordMutationSupport mutations;
    private final IdService ids;
    private final ObjectMapper mapper;
    private final ObjectProvider<PrintAssetReader> assetReaders;

    public PrintService(RecordRuntimeService records, PrintRepository repository, PrintRenderer renderer,
                        DurableJobFacade jobs, RecordMutationSupport mutations, IdService ids, ObjectMapper mapper,
                        ObjectProvider<PrintAssetReader> assetReaders) {
        this.records = records;
        this.repository = repository;
        this.renderer = renderer;
        this.jobs = jobs;
        this.mutations = mutations;
        this.ids = ids;
        this.mapper = mapper;
        this.assetReaders = assetReaders;
    }

    public List<PrintViews.RuntimeTemplate> templates(RuntimeSession session, String moduleCode, long recordId) {
        var schema = records.printSchema(session, moduleCode);
        records.detail(session, moduleCode, recordId);
        return repository.runtimeTemplates(session.systemId(), moduleCode, Long.parseLong(schema.schemaVersionId()))
                .stream().map(this::runtimeView).toList();
    }

    public PrintViews.Preview preview(RuntimeSession session, String moduleCode, long recordId,
                                      PrintViews.PreviewRequest request) {
        var prepared = prepare(session, moduleCode, recordId, request == null ? null : request.templateCode(), null);
        return new PrintViews.Preview(prepared.template().code(), Long.toString(prepared.template().id()),
                prepared.template().versionNo(), Long.toString(recordId), prepared.detail().version(),
                renderer.html(prepared.snapshot()));
    }

    @Transactional
    public PrintViews.Task create(RuntimeSession session, String moduleCode, long recordId,
                                  PrintViews.CreatePrintRequest request, String idempotencyKey,
                                  String requestId, String traceId) {
        var scope = "print:" + session.systemId() + ":" + requiredTenant(session) + ":" + session.memberId()
                + ":" + moduleCode + ":" + recordId;
        return mutations.idempotent(scope, idempotencyKey, request, PrintViews.Task.class, 202,
                () -> createNow(session, moduleCode, recordId, request, requestId, traceId));
    }

    private PrintViews.Task createNow(RuntimeSession session, String moduleCode, long recordId,
                                      PrintViews.CreatePrintRequest request, String requestId, String traceId) {
        if (request == null || request.expectedRecordVersion() <= 0) {
            throw invalid("PRINT_REQUEST_INVALID", "Template code and expected record version are required");
        }
        var prepared = prepare(session, moduleCode, recordId, request.templateCode(), request.expectedRecordVersion());
        var printId = ids.nextId();
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(JOB_TYPE, "MODULE_PRINT_TASK",
                Long.toString(printId), session.systemId(), requiredTenant(session), session.memberId(),
                Map.of("printId", Long.toString(printId)), 3));
        var now = LocalDateTime.now();
        var schema = prepared.schema();
        var template = prepared.template();
        var snapshotJson = repository.write(prepared.snapshot());
        if (snapshotJson.getBytes(StandardCharsets.UTF_8).length > 500_000) {
            throw invalid("PRINT_SNAPSHOT_TOO_LARGE", "Printable record snapshot exceeds the first-slice limit");
        }
        repository.insertTask(new PrintRepository.TaskRecord(printId, session.systemId(), requiredTenant(session),
                Long.parseLong(schema.logicalModuleId()), moduleCode, recordId, prepared.detail().version(),
                prepared.detail().recordNo(), template.templateId(), template.id(), template.versionNo(),
                template.code(), template.name(), template.schemaVersionId(), template.moduleSnapshotId(),
                snapshotJson, "QUEUED", null, null, null, null, job.id(),
                session.accountId(), session.memberId(), requestId, traceId, null, null, now, now, 0));
        return view(repository.requireTask(printId));
    }

    public PrintViews.TaskPage list(RuntimeSession session, String moduleCode, long recordId, int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw invalid("PRINT_PAGE_INVALID", "Print history page must be >=1 and size must be 1..50");
        }
        records.printSchema(session, moduleCode);
        records.detail(session, moduleCode, recordId);
        var tenantId = requiredTenant(session);
        var total = repository.countTasks(session.systemId(), tenantId, moduleCode, recordId, session.memberId());
        var offset = Math.multiplyExact((long) page - 1, size);
        var items = repository.pageTasks(session.systemId(), tenantId, moduleCode, recordId, session.memberId(),
                size, offset).stream().map(this::view).toList();
        return new PrintViews.TaskPage(items, page, size, total);
    }

    public PrintViews.Task get(RuntimeSession session, String moduleCode, long recordId, long printId) {
        var task = owned(session, moduleCode, recordId, printId);
        records.printSchema(session, moduleCode);
        records.detail(session, moduleCode, recordId);
        return view(task);
    }

    public Result result(RuntimeSession session, String moduleCode, long recordId, long printId) {
        var task = owned(session, moduleCode, recordId, printId);
        records.printSchema(session, moduleCode);
        records.detail(session, moduleCode, recordId);
        if (!"SUCCEEDED".equals(task.status())) {
            throw new BusinessException("PRINT_RESULT_NOT_READY", "Print PDF is not ready", HttpStatus.CONFLICT);
        }
        return new Result(task.resultFilename(), repository.result(task.id()));
    }

    private Prepared prepare(RuntimeSession session, String moduleCode, long recordId, String templateCode,
                             Long expectedRecordVersion) {
        if (templateCode == null || !templateCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw invalid("PRINT_TEMPLATE_CODE_INVALID", "A valid published template code is required");
        }
        var schema = records.printSchema(session, moduleCode);
        var detail = records.detail(session, moduleCode, recordId);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(detail.status())) {
            throw invalid("PRINT_RECORD_STATUS_INVALID", "Only active or archived records can be printed");
        }
        if (expectedRecordVersion != null && detail.version() != expectedRecordVersion) {
            throw new BusinessException("PRINT_RECORD_VERSION_CONFLICT", "Record changed; reload before printing",
                    HttpStatus.CONFLICT);
        }
        var template = repository.requireRuntimeTemplate(session.systemId(), moduleCode, templateCode,
                Long.parseLong(schema.schemaVersionId()));
        if (template.moduleSnapshotId() != Long.parseLong(schema.moduleSnapshotId())) {
            throw new BusinessException("PRINT_TEMPLATE_SCHEMA_STALE", "Print template must be republished",
                    HttpStatus.CONFLICT);
        }
        var definition = repository.definition(template.definitionJson());
        var fields = new LinkedHashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> fields.put(field.fieldCode(), field));
        var values = new LinkedHashMap<String, RecordRuntimeViews.FieldValue>();
        detail.values().forEach(value -> values.put(value.fieldCode(), value));
        var lines = new java.util.ArrayList<PrintViews.Line>();
        var tables = new java.util.ArrayList<PrintViews.Table>();
        for (var code : definition.fieldCodes()) {
            var field = fields.get(code);
            if (field == null || !field.readable() || field.masked() || FORBIDDEN_TYPES.contains(field.type())) {
                throw new BusinessException("PRINT_TEMPLATE_FIELD_FORBIDDEN",
                        "Template field authorization changed: " + code, HttpStatus.CONFLICT);
            }
            if ("RELATION".equals(field.type())) {
                tables.add(relationTable(session, moduleCode, recordId, field));
                continue;
            }
            if ("SUBTABLE".equals(field.type())) {
                tables.add(subtableTable(session, moduleCode, recordId, field));
                continue;
            }
            var value = values.get(code);
            lines.add(new PrintViews.Line(field.fieldName(), code, display(value)));
        }
        var title = definition.title().replace("{title}", safe(detail.title()))
                .replace("{recordNo}", safe(detail.recordNo()));
        var subtitle = safe(detail.recordNo()) + " · " + detail.status() + " · v" + detail.version();
        var header = replaceTokens(definition.header(), detail, recordId);
        var footer = replaceTokens(definition.footer(), detail, recordId);
        var codes = definition.codeBlocks().stream().map(block -> new PrintViews.ResolvedCodeBlock(
                block.kind(), block.label(), codeValue(block.fieldCode(), values, detail, recordId))).toList();
        var positioned = definition.positionedElements().stream().map(item -> {
            var resolved = item.fieldCode() == null || item.fieldCode().isBlank() ? ""
                    : display(values.get(item.fieldCode()));
            var image = positionedImage(session, item, values);
            return new PrintViews.ResolvedPositionedElement(item.kind(), item.label()
                    + (resolved.isBlank() || image != null ? "" : "\n" + resolved), item.fieldCode(), item.xMm(),
                    item.yMm(), item.widthMm(), item.heightMm(), image == null ? null : image.dataUri(),
                    image == null ? null : image.sha256());
        }).toList();
        return new Prepared(schema, detail, template, new PrintViews.Snapshot(header, title, subtitle,
                footer, template.paperSize(), template.orientation(), lines, tables,
                positioned, codes));
    }

    private EmbeddedImage positionedImage(RuntimeSession session, PrintViews.PositionedElement item,
                                           Map<String, RecordRuntimeViews.FieldValue> values) {
        if (!Set.of("SIGNATURE", "SEAL").contains(item.kind()) || item.fieldCode() == null) return null;
        var value = values.get(item.fieldCode());
        if (value == null || !Set.of("SIGNATURE", "IMAGE").contains(value.type())
                || !(value.value() instanceof List<?> ids) || ids.isEmpty()) return null;
        var reader = assetReaders.getIfAvailable();
        if (reader == null) return null;
        try {
            var fileId = Long.parseLong(String.valueOf(ids.getFirst()));
            var image = reader.thumbnail(session.systemId(), requiredTenant(session), session.memberId(),
                    session.permissions(), fileId);
            return new EmbeddedImage("data:" + image.mediaType() + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(image.content()), image.sha256());
        } catch (RuntimeException exception) {
            throw new BusinessException("PRINT_POSITIONED_ASSET_UNAVAILABLE",
                    "Signature/seal image is unavailable or forbidden", HttpStatus.CONFLICT);
        }
    }

    private PrintViews.Table relationTable(RuntimeSession session, String moduleCode, long recordId,
                                            RecordRuntimeViews.FieldCapability field) {
        var rows = new java.util.ArrayList<List<String>>();
        var page = 1;
        long total;
        do {
            var slice = records.relations(session, moduleCode, recordId, field.fieldCode(), page, 100, "print");
            total = Math.min(slice.total(), MAX_COMPOSITION_ROWS);
            slice.items().stream().limit(Math.max(0, MAX_COMPOSITION_ROWS - rows.size()))
                    .forEach(item -> rows.add(List.of(item.title(), item.targetRecordId())));
            page++;
        } while (rows.size() < total);
        return new PrintViews.Table(field.fieldName(), field.fieldCode(), List.of("关联显示值", "记录标识"), rows);
    }

    private PrintViews.Table subtableTable(RuntimeSession session, String moduleCode, long recordId,
                                            RecordRuntimeViews.FieldCapability field) {
        var items = new java.util.ArrayList<RecordRuntimeViews.SubRowResponse>();
        var page = 1;
        long total;
        do {
            var slice = records.subtable(session, moduleCode, recordId, field.fieldCode(), page, 100, "print");
            total = Math.min(slice.total(), MAX_COMPOSITION_ROWS);
            slice.items().stream().limit(Math.max(0, MAX_COMPOSITION_ROWS - items.size())).forEach(items::add);
            page++;
        } while (items.size() < total);
        var columns = new java.util.ArrayList<String>();
        items.forEach(item -> item.values().forEach(value -> {
            if (!columns.contains(value.fieldName())) columns.add(value.fieldName());
        }));
        var rows = items.stream().map(item -> {
            var byName = new LinkedHashMap<String, RecordRuntimeViews.FieldValue>();
            item.values().forEach(value -> byName.put(value.fieldName(), value));
            return columns.stream().map(column -> display(byName.get(column))).toList();
        }).toList();
        return new PrintViews.Table(field.fieldName(), field.fieldCode(), columns, rows);
    }

    private String codeValue(String source, Map<String, RecordRuntimeViews.FieldValue> values,
                             RecordRuntimeViews.RecordDetail detail, long recordId) {
        if ("{recordNo}".equals(source)) return safe(detail.recordNo());
        if ("{recordId}".equals(source)) return Long.toString(recordId);
        return display(values.get(source));
    }

    private static String replaceTokens(String value, RecordRuntimeViews.RecordDetail detail, long recordId) {
        return safe(value).replace("{title}", safe(detail.title())).replace("{recordNo}", safe(detail.recordNo()))
                .replace("{recordId}", Long.toString(recordId));
    }

    private String display(RecordRuntimeViews.FieldValue value) {
        if (value == null) return "";
        if (value.displayValue() != null) return value.displayValue();
        if (value.value() == null) return "";
        if (value.value() instanceof String text) return text;
        try { return mapper.writeValueAsString(value.value()); }
        catch (Exception exception) { return String.valueOf(value.value()); }
    }

    PrintViews.Task view(PrintRepository.TaskRecord task) {
        return new PrintViews.Task(Long.toString(task.id()), Long.toString(task.recordId()), task.recordVersion(),
                task.recordNo(), task.templateCode(), task.templateName(), Long.toString(task.templateVersionId()),
                task.templateVersionNo(), Long.toString(task.schemaVersionId()), task.status(),
                Long.toString(task.jobId()), task.resultFilename(), task.resultSize(), task.failureCode(),
                task.failureMessage(), task.createdAt(), task.startedAt(), task.finishedAt());
    }

    private PrintViews.RuntimeTemplate runtimeView(PrintRepository.TemplateVersionRecord template) {
        return new PrintViews.RuntimeTemplate(Long.toString(template.templateId()), template.code(), template.name(),
                template.paperSize(), template.orientation(), Long.toString(template.id()), template.versionNo(),
                Long.toString(template.schemaVersionId()));
    }

    private PrintRepository.TaskRecord owned(RuntimeSession session, String moduleCode, long recordId, long printId) {
        var task = repository.requireTask(printId);
        if (task.systemId() != session.systemId() || task.tenantId() != requiredTenant(session)
                || task.memberId() != session.memberId() || task.recordId() != recordId
                || !task.moduleCode().equals(moduleCode)) {
            throw new BusinessException("PRINT_NOT_FOUND", "Print record was not found", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private record Prepared(RecordRuntimeViews.RecordSchema schema, RecordRuntimeViews.RecordDetail detail,
                            PrintRepository.TemplateVersionRecord template, PrintViews.Snapshot snapshot) { }
    private record EmbeddedImage(String dataUri, String sha256) { }
    public record Result(String filename, byte[] content) { }
}
