package com.unique.examine.module.manage.importexport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.base.entity.SysAsyncTaskEvent;
import com.unique.examine.core.base.service.SysAsyncTaskEventBaseService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.manage.task.PersistedAsyncTaskService;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.entity.ModuleDynamicRecord;
import com.unique.examine.module.base.entity.ModuleDynamicValue;
import com.unique.examine.module.base.entity.ModuleFieldDefinition;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleDynamicRecordBaseService;
import com.unique.examine.module.base.service.ModuleDynamicValueBaseService;
import com.unique.examine.module.base.service.ModuleFieldDefinitionBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.importexport.ImportExportModels.AsyncTask;
import com.unique.examine.module.manage.importexport.ImportExportModels.ExportRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ExportResult;
import com.unique.examine.module.manage.importexport.ImportExportModels.FileRef;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportConfirmRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportConfirmResult;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportIssue;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportPrecheckRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportPrecheckResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSaveRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordService;
import com.unique.examine.upload.manage.UploadManageModels.UploadFileVO;
import com.unique.examine.upload.manage.UploadManageService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Import/export execution service backed by async tasks and upload file references.
 */
@Service
public class ImportExportService {

    private static final long SYSTEM_OPERATOR_ID = 0L;
    private static final String DEFAULT_TEMPLATE_CODE = "runtime_import_v1";
    private static final TypeReference<List<ImportIssue>> IMPORT_ISSUE_LIST = new TypeReference<>() {
    };

    private final ModuleSystemContextResolver contextResolver;
    private final UploadManageService uploadManageService;
    private final PersistedAsyncTaskService asyncTaskService;
    private final SysAsyncTaskEventBaseService taskEventBaseService;
    private final ModuleDefinitionBaseService moduleBaseService;
    private final ModuleFieldDefinitionBaseService fieldBaseService;
    private final ModuleDynamicRecordBaseService recordBaseService;
    private final ModuleDynamicValueBaseService valueBaseService;
    private final RuntimeRecordService runtimeRecordService;
    private final ObjectMapper objectMapper;

    public ImportExportService(ModuleSystemContextResolver contextResolver,
                               UploadManageService uploadManageService,
                               PersistedAsyncTaskService asyncTaskService,
                               SysAsyncTaskEventBaseService taskEventBaseService,
                               ModuleDefinitionBaseService moduleBaseService,
                               ModuleFieldDefinitionBaseService fieldBaseService,
                               ModuleDynamicRecordBaseService recordBaseService,
                               ModuleDynamicValueBaseService valueBaseService,
                               RuntimeRecordService runtimeRecordService,
                               ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.uploadManageService = uploadManageService;
        this.asyncTaskService = asyncTaskService;
        this.taskEventBaseService = taskEventBaseService;
        this.moduleBaseService = moduleBaseService;
        this.fieldBaseService = fieldBaseService;
        this.recordBaseService = recordBaseService;
        this.valueBaseService = valueBaseService;
        this.runtimeRecordService = runtimeRecordService;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute import precheck and persist the result as task/event/file records.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request precheck request
     * @param idempotencyKey idempotency key
     * @return precheck result
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportPrecheckResult precheck(String systemId, String moduleId, ImportPrecheckRequest request,
                                         String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        String suffix = shortTrace(RequestContext.current().traceId());
        CsvData csvData = readCsvData(request);
        List<ImportIssue> issues = precheckIssues(request, csvData, fieldsForModule(module.getId()));
        boolean passed = issues.stream().noneMatch(issue -> "ERROR".equals(issue.level()));
        FileRef resultFile = generatedFile("file_precheck_result_" + suffix, "import-precheck-result.csv",
                "csv", context, precheckCsv(issues));
        FileRef errorFile = generatedFile("file_precheck_error_" + suffix, "import-precheck-errors.csv",
                "csv", context, precheckCsv(issues));
        String resolvedIdempotencyKey = safeText(idempotencyKey, "idem_precheck_" + suffix);
        String taskId = "task_import_precheck_" + suffix;
        AsyncTaskView taskView = asyncTaskService.upsert(taskId, "IMPORT_PRECHECK", resolvedIdempotencyKey,
                passed ? AsyncTaskStatus.SUCCESS : AsyncTaskStatus.FAILED, 100, true, false, false,
                resultFile.fileId(), errorFile.fileId(),
                passed ? null : "导入预检未通过", 0, issues.size(), SYSTEM_OPERATOR_ID,
                Map.of("systemId", context.systemId(), "tenantId", context.tenantId(), "moduleId", moduleId));
        String precheckId = taskView.taskId();
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("precheckId", precheckId);
        eventPayload.put("templateCode", safeText(Objects.isNull(request) ? null : request.templateCode(),
                DEFAULT_TEMPLATE_CODE));
        eventPayload.put("passed", passed);
        eventPayload.put("issues", issues);
        eventPayload.put("sourceFileId", Objects.isNull(request) ? null : request.fileId());
        eventPayload.put("fieldMapping", Objects.isNull(request) ? Map.of() : safeMap(request.fieldMapping()));
        asyncTaskService.saveEvent(precheckId, "IMPORT_PRECHECK_RESULT", eventPayload);
        AsyncTask task = toTask(taskView, resultFile, errorFile);
        int totalRows = csvData.rows().size();
        int invalidRows = (int) issues.stream().filter(issue -> "ERROR".equals(issue.level())).count();
        return new ImportPrecheckResult(precheckId, passed, totalRows, passed ? totalRows : 0,
                invalidRows, 0, issues, resultFile, errorFile, task,
                RequestContext.current().traceId(), auditLogId(RequestContext.current()));
    }

    /**
     * Confirm import execution and persist an import task.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request confirm request
     * @param idempotencyKey idempotency key
     * @return confirm result
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportConfirmResult confirm(String systemId, String moduleId, ImportConfirmRequest request,
                                       String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        String precheckId = safeText(Objects.isNull(request) ? null : request.precheckId(), null);
        if (!StringUtils.hasText(precheckId)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "预检ID不能为空");
        }
        PrecheckPayload precheckPayload = precheckPayload(precheckId);
        List<ImportIssue> precheckWarnings = precheckPayload.issues().stream()
                .filter(issue -> "WARN".equals(issue.level()))
                .toList();
        CsvData csvData = readCsvData(new ImportPrecheckRequest(precheckPayload.sourceFileId(),
                DEFAULT_TEMPLATE_CODE, precheckPayload.fieldMapping(), request == null ? null : request.duplicateStrategy()));
        int successCount = 0;
        for (Map<String, String> row : csvData.rows()) {
            Map<String, Object> values = mappedValues(row, precheckPayload.fieldMapping());
            runtimeRecordService.create(systemId, moduleId,
                    new RecordSaveRequest(values, Map.of(), List.of(), null, "IMPORT_CSV"),
                    safeText(idempotencyKey, "idem_import_" + precheckId) + "_" + successCount);
            successCount++;
        }
        String suffix = shortTrace(RequestContext.current().traceId());
        FileRef resultFile = generatedFile("file_import_result_" + suffix, "import-result.csv", "csv", context,
                ("result,count\nSUCCESS," + successCount + "\n").getBytes(StandardCharsets.UTF_8));
        FileRef errorFile = generatedFile("file_import_error_" + suffix, "import-errors.csv", "csv", context,
                precheckCsv(precheckWarnings));
        boolean rollbackSupported = Objects.nonNull(request) && Boolean.TRUE.equals(request.rollbackSupported());
        AsyncTaskView taskView = asyncTaskService.upsert("task_import_confirm_" + suffix,
                "IMPORT_CONFIRM", safeText(idempotencyKey, "idem_import_" + suffix),
                AsyncTaskStatus.SUCCESS, 100, false, false, rollbackSupported,
                resultFile.fileId(), errorFile.fileId(), null, successCount, 0, SYSTEM_OPERATOR_ID,
                Map.of("systemId", context.systemId(), "tenantId", context.tenantId(), "moduleId", moduleId,
                        "precheckId", precheckId));
        return new ImportConfirmResult(precheckId, toTask(taskView, resultFile, errorFile), precheckWarnings);
    }

    /**
     * Create an export task and expected result/error files.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request export request
     * @param idempotencyKey idempotency key
     * @return export result
     */
    @Transactional(rollbackFor = Exception.class)
    public ExportResult export(String systemId, String moduleId, ExportRequest request, String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        String suffix = shortTrace(RequestContext.current().traceId());
        String fileFormat = "csv";
        byte[] exportContent = exportCsv(context, module, request);
        FileRef resultFile = generatedFile("file_export_result_" + suffix,
                "runtime-export." + fileFormat, fileFormat, context, exportContent);
        FileRef errorFile = generatedFile("file_export_error_" + suffix, "runtime-export-errors.csv",
                "csv", context, "message\n\n".getBytes(StandardCharsets.UTF_8));
        AsyncTaskView taskView = asyncTaskService.upsert("task_runtime_record_export_" + suffix,
                "RUNTIME_RECORD_EXPORT", safeText(idempotencyKey, "idem_export_" + suffix),
                AsyncTaskStatus.SUCCESS, 100, false, false, false,
                resultFile.fileId(), errorFile.fileId(), null, 0, 0, SYSTEM_OPERATOR_ID,
                Map.of("systemId", context.systemId(), "tenantId", context.tenantId(), "moduleId", moduleId,
                        "scope", safeText(Objects.isNull(request) ? null : request.scope(), "CURRENT_FILTER")));
        return new ExportResult(toTask(taskView, resultFile, errorFile),
                safeText(Objects.isNull(request) ? null : request.scope(), "CURRENT_FILTER"),
                exportFields(request), safeText(Objects.isNull(request) ? null : request.desensitizeMode(), "MASK"),
                resultFile, errorFile);
    }

    private List<ImportIssue> precheckIssues(ImportPrecheckRequest request, CsvData csvData,
                                             List<ModuleFieldDefinition> fields) {
        if (Objects.isNull(request) || !StringUtils.hasText(request.fileId())) {
            return List.of(new ImportIssue(0, "fileId", "ERROR", "导入文件不能为空",
                    "请先上传导入文件后再执行预检"));
        }
        List<ImportIssue> issues = new ArrayList<>();
        Map<String, ModuleFieldDefinition> fieldByCode = new LinkedHashMap<>();
        fields.forEach(field -> fieldByCode.put(field.getFieldCode(), field));
        if (Objects.nonNull(request.fieldMapping()) && request.fieldMapping().containsValue(null)) {
            issues.add(new ImportIssue(0, "fieldMapping", "ERROR", "字段映射存在空目标字段",
                    "请补齐源列到模块字段的映射"));
        }
        for (String targetField : safeMap(request.fieldMapping()).values()) {
            if (StringUtils.hasText(targetField) && !fieldByCode.containsKey(targetField)) {
                issues.add(new ImportIssue(0, targetField, "ERROR", "目标字段不存在",
                        "请检查字段映射是否使用了已发布字段编码"));
            }
        }
        if (csvData.headers().isEmpty()) {
            issues.add(new ImportIssue(1, "file", "ERROR", "CSV 文件没有表头",
                    "请上传第一行为字段名的 CSV 文件"));
        }
        if (csvData.rows().isEmpty()) {
            issues.add(new ImportIssue(2, "file", "WARN", "CSV 文件没有可导入数据行",
                    "请确认文件内容后再导入"));
        }
        return issues;
    }

    private List<ImportIssue> precheckWarnings(String precheckId) {
        return precheckPayload(precheckId).issues().stream()
                .filter(issue -> "WARN".equals(issue.level()))
                .toList();
    }

    private PrecheckPayload precheckPayload(String precheckId) {
        SysAsyncTaskEvent event = taskEventBaseService.getOne(new LambdaQueryWrapper<SysAsyncTaskEvent>()
                .eq(SysAsyncTaskEvent::getTaskId, precheckId)
                .eq(SysAsyncTaskEvent::getEventType, "IMPORT_PRECHECK_RESULT")
                .orderByDesc(SysAsyncTaskEvent::getCreatedAt)
                .last("LIMIT 1"), false);
        if (Objects.isNull(event)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "导入预检结果不存在");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getEventPayload(), new TypeReference<>() {
            });
            String issuesJson = objectMapper.writeValueAsString(payload.get("issues"));
            List<ImportIssue> issues = objectMapper.readValue(issuesJson, IMPORT_ISSUE_LIST);
            Map<String, String> fieldMapping = objectMapper.convertValue(payload.get("fieldMapping"),
                    new TypeReference<Map<String, String>>() {
                    });
            Object sourceFileId = payload.get("sourceFileId");
            return new PrecheckPayload(Objects.isNull(sourceFileId) ? null : String.valueOf(sourceFileId),
                    Objects.isNull(fieldMapping) ? Map.of() : fieldMapping, issues);
        } catch (Exception ex) {
            return new PrecheckPayload(null, Map.of(), List.of());
        }
    }

    private FileRef generatedFile(String fileId, String fileName, String fileType, ModuleSystemContext context) {
        return generatedFile(fileId, fileName, fileType, context, null);
    }

    private FileRef generatedFile(String fileId, String fileName, String fileType, ModuleSystemContext context,
                                  byte[] content) {
        UploadFileVO file = uploadManageService.registerGeneratedFile(fileId, fileName, fileType,
                "sha256:" + fileId, context.systemId(), context.tenantId(), context.systemMemberId(), content);
        return new FileRef(file.fileId(), file.fileName(), fileType, file.downloadUrl(), file.checksum(),
                file.uploadedAt());
    }

    private CsvData readCsvData(ImportPrecheckRequest request) {
        if (Objects.isNull(request) || !StringUtils.hasText(request.fileId())) {
            return new CsvData(List.of(), List.of());
        }
        UploadFileVO file = uploadManageService.getFile(request.fileId());
        if (!"csv".equalsIgnoreCase(file.fileType()) && !"text/csv".equalsIgnoreCase(file.fileType())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "当前导入执行先支持 CSV 文件");
        }
        String content = new String(uploadManageService.readFileBytes(request.fileId(), 10L * 1024 * 1024),
                StandardCharsets.UTF_8);
        List<String> lines = Arrays.stream(content.split("\\R"))
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            return new CsvData(List.of(), List.of());
        }
        List<String> headers = parseCsvLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            List<String> cells = parseCsvLine(lines.get(index));
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), column < cells.size() ? cells.get(column) : "");
            }
            rows.add(row);
        }
        return new CsvData(headers, rows);
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '"') {
                quoted = !quoted;
            } else if (value == ',' && !quoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(value);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private Map<String, Object> mappedValues(Map<String, String> row, Map<String, String> fieldMapping) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (fieldMapping.isEmpty()) {
            values.putAll(row);
            return values;
        }
        fieldMapping.forEach((source, target) -> {
            if (StringUtils.hasText(target) && row.containsKey(source)) {
                values.put(target, row.get(source));
            }
        });
        return values;
    }

    private ModuleDefinition requireModule(ModuleSystemContext context, String moduleId) {
        Long id = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        ModuleDefinition module = moduleBaseService.getOne(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getId, id)
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getDeleted, 0)
                .last("LIMIT 1"), false);
        if (Objects.isNull(module)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模块不存在");
        }
        return module;
    }

    private List<ModuleFieldDefinition> fieldsForModule(Long moduleId) {
        return fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, 0)
                .eq(ModuleFieldDefinition::getStatus, 1)
                .orderByAsc(ModuleFieldDefinition::getSortOrder)
                .orderByAsc(ModuleFieldDefinition::getId));
    }

    private byte[] exportCsv(ModuleSystemContext context, ModuleDefinition module, ExportRequest request) {
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        List<String> requestedFields = exportFields(request);
        List<ModuleDynamicRecord> records = recordsForExport(context, module, request);
        StringBuilder builder = new StringBuilder();
        builder.append("recordId,title,status,updatedAt");
        for (ModuleFieldDefinition field : fields) {
            if (requestedFields.contains(field.getFieldCode())) {
                builder.append(',').append(escapeCsv(field.getFieldCode()));
            }
        }
        builder.append('\n');
        for (ModuleDynamicRecord record : records) {
            Map<Long, ModuleDynamicValue> values = valuesByField(record.getId());
            builder.append(record.getId()).append(',')
                    .append(escapeCsv(record.getTitle())).append(',')
                    .append(escapeCsv(record.getStatus())).append(',')
                    .append(record.getUpdatedAt());
            for (ModuleFieldDefinition field : fields) {
                if (requestedFields.contains(field.getFieldCode())) {
                    builder.append(',').append(escapeCsv(valueOf(values.get(field.getId()))));
                }
            }
            builder.append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<ModuleDynamicRecord> recordsForExport(ModuleSystemContext context, ModuleDefinition module,
                                                       ExportRequest request) {
        LambdaQueryWrapper<ModuleDynamicRecord> wrapper = new LambdaQueryWrapper<ModuleDynamicRecord>()
                .eq(ModuleDynamicRecord::getSystemId, context.systemId())
                .eq(ModuleDynamicRecord::getTenantId, context.tenantId())
                .eq(ModuleDynamicRecord::getModuleId, module.getId())
                .eq(ModuleDynamicRecord::getDeleted, 0)
                .orderByDesc(ModuleDynamicRecord::getUpdatedAt);
        List<Long> selectedIds = selectedRecordIds(request);
        if (!selectedIds.isEmpty()) {
            wrapper.in(ModuleDynamicRecord::getId, selectedIds);
        }
        return recordBaseService.list(wrapper);
    }

    private List<Long> selectedRecordIds(ExportRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.selectedRecordIds())
                || request.selectedRecordIds().isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String recordId : request.selectedRecordIds()) {
            try {
                ids.add(Long.valueOf(recordId));
            } catch (NumberFormatException ex) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "导出记录ID格式不正确");
            }
        }
        return ids;
    }

    private Map<Long, ModuleDynamicValue> valuesByField(Long recordId) {
        Map<Long, ModuleDynamicValue> values = new LinkedHashMap<>();
        for (ModuleDynamicValue value : valueBaseService.list(new LambdaQueryWrapper<ModuleDynamicValue>()
                .eq(ModuleDynamicValue::getRecordId, recordId))) {
            values.put(value.getFieldId(), value);
        }
        return values;
    }

    private Object valueOf(ModuleDynamicValue value) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (StringUtils.hasText(value.getValueJson())) {
            try {
                return objectMapper.readValue(value.getValueJson(), Object.class);
            } catch (Exception ex) {
                return value.getValueJson();
            }
        }
        if (Objects.nonNull(value.getValueNumber())) {
            return value.getValueNumber();
        }
        if (Objects.nonNull(value.getValueDatetime())) {
            return value.getValueDatetime();
        }
        return value.getValueText();
    }

    private byte[] precheckCsv(List<ImportIssue> issues) {
        StringBuilder builder = new StringBuilder("rowNo,fieldCode,level,message,suggestion\n");
        for (ImportIssue issue : issues) {
            builder.append(issue.rowNo()).append(',')
                    .append(escapeCsv(issue.fieldCode())).append(',')
                    .append(escapeCsv(issue.level())).append(',')
                    .append(escapeCsv(issue.message())).append(',')
                    .append(escapeCsv(issue.suggestion())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(Object value) {
        if (Objects.isNull(value)) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private AsyncTask toTask(AsyncTaskView task, FileRef resultFile, FileRef errorFile) {
        return new AsyncTask(task.taskId(), task.bizType(), task.idempotencyKey(), task.status().name(),
                task.progress(), task.retryable(), task.cancelable(), resultFile, errorFile, task.failureReason(),
                task.partialSuccessCount(), task.partialFailureCount(), task.rollbackSupported(), task.traceId(),
                task.auditLogId(), task.createdBy(),
                StringUtils.hasText(task.createdAt()) ? LocalDateTime.parse(task.createdAt()) : LocalDateTime.now());
    }

    private List<String> exportFields(ExportRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.fields()) || request.fields().isEmpty()) {
            return List.of("title", "status", "updatedAt");
        }
        return request.fields();
    }

    private Map<String, String> safeMap(Map<String, String> value) {
        return Objects.isNull(value) ? Map.of() : value;
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        return Objects.isNull(traceId) || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record CsvData(List<String> headers, List<Map<String, String>> rows) {
    }

    private record PrecheckPayload(String sourceFileId, Map<String, String> fieldMapping,
                                   List<ImportIssue> issues) {
    }
}
