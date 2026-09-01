package com.unique.unexamine.dataexchange.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobExecution;
import com.unique.unexamine.dataexchange.base.entity.ExchangeImportBatch;
import com.unique.unexamine.dataexchange.base.entity.ExchangeImportRow;
import com.unique.unexamine.dataexchange.base.entity.ExchangeMapping;
import com.unique.unexamine.dataexchange.base.service.ExchangeImportBatchBaseService;
import com.unique.unexamine.dataexchange.base.service.ExchangeImportRowBaseService;
import com.unique.unexamine.dataexchange.base.service.ExchangeMappingBaseService;
import com.unique.unexamine.file.manage.FileModels;
import com.unique.unexamine.file.manage.FileStorageService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.runtimedata.manage.CreateRuntimeRecordRequest;
import com.unique.unexamine.runtimedata.manage.RecordLifecycleRequest;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeImportValidation;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.runtimedata.manage.UpdateRuntimeRecordRequest;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ModuleImportService {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final int MAX_ROWS = 500;

    private final ExchangeMappingBaseService mappings;
    private final ExchangeImportBatchBaseService batches;
    private final ExchangeImportRowBaseService rows;
    private final ModulePublicationService publicationService;
    private final RuntimeDataService runtimeDataService;
    private final FileStorageService fileStorageService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ModuleImportService(
            ExchangeMappingBaseService mappings,
            ExchangeImportBatchBaseService batches,
            ExchangeImportRowBaseService rows,
            ModulePublicationService publicationService,
            RuntimeDataService runtimeDataService,
            FileStorageService fileStorageService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.mappings = mappings;
        this.batches = batches;
        this.rows = rows;
        this.publicationService = publicationService;
        this.runtimeDataService = runtimeDataService;
        this.fileStorageService = fileStorageService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public ImportModels.TemplateView template(AuthenticatedContext context, String moduleCode, String traceId) {
        RuntimeModuleConfiguration configuration = requireImport(context, moduleCode, traceId);
        List<ImportModels.TemplateColumn> columns = new ArrayList<>();
        columns.add(new ImportModels.TemplateColumn("title", "标题", "TEXT", true, true, true));
        columns.add(new ImportModels.TemplateColumn("recordNumber", "业务编号", "TEXT", false, true, true));
        columns.add(new ImportModels.TemplateColumn("status", "状态", "TEXT", false, true, true));
        configuration.configuration().path("fields").forEach(field -> {
            boolean writable = field.path("access").path("writable").asBoolean(false);
            if ("ACTIVE".equals(field.path("status").asText("ACTIVE")) && writable) {
                columns.add(new ImportModels.TemplateColumn(field.path("code").asText(), field.path("name").asText(),
                        field.path("fieldType").asText(), field.path("required").asBoolean(), true, false));
            }
        });
        String header = columns.stream().map(ImportModels.TemplateColumn::code)
                .map(this::csvCell).reduce((left, right) -> left + "," + right).orElse("title");
        String sample = columns.stream().map(column -> column.required() ? "示例" : "")
                .map(this::csvCell).reduce((left, right) -> left + "," + right).orElse("示例");
        return new ImportModels.TemplateView(moduleCode, configuration.moduleId(), configuration.versionNumber(),
                columns, header, sample);
    }

    @Transactional
    public ImportModels.ImportBatchView preview(
            AuthenticatedContext context,
            String moduleCode,
            ImportModels.PreviewRequest input,
            String traceId) {
        RuntimeModuleConfiguration configuration = requireImport(context, moduleCode, traceId);
        FileModels.FileView source = fileStorageService.detail(context, input.sourceFileId(), traceId);
        if (!"ACTIVE".equals(source.status()) || !"CLEAN".equals(source.scanStatus())) {
            throw conflict("IMPORT_SOURCE_FILE_UNSAFE", "导入文件尚未通过安全扫描");
        }
        if (source.references().isEmpty()) {
            throw conflict("IMPORT_SOURCE_FILE_UNREFERENCED", "导入文件必须先保存业务引用");
        }
        byte[] bytes = fileStorageService.download(context, source.id(), traceId).bytes();
        List<List<String>> csv = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        if (csv.size() < 2) throw invalid("IMPORT_FILE_EMPTY", "导入文件必须包含表头和至少一行数据");
        if (csv.size() - 1 > MAX_ROWS) throw invalid("IMPORT_ROW_LIMIT_EXCEEDED", "单批预演最多支持 500 行");

        List<String> headers = csv.getFirst().stream().map(String::strip).toList();
        if (headers.stream().anyMatch(String::isBlank) || new LinkedHashSet<>(headers).size() != headers.size()) {
            throw invalid("IMPORT_HEADER_INVALID", "导入表头不能为空或重复");
        }
        Map<String, ImportModels.TemplateColumn> targets = new LinkedHashMap<>();
        template(context, moduleCode, traceId).columns().forEach(column -> targets.put(column.code(), column));
        Map<String, String> mapping = normalizedMapping(headers, input.columnMapping(), targets.keySet());
        if (!mapping.containsValue("title")) throw invalid("IMPORT_TITLE_MAPPING_REQUIRED", "必须映射标题列");
        String conflictPolicy = input.conflictPolicy() == null ? "ERROR" : input.conflictPolicy();

        ExchangeMapping mappingEntity = new ExchangeMapping();
        mappingEntity.setSystemId(context.systemId());
        mappingEntity.setOwnerTenantId(context.tenantId());
        mappingEntity.setModuleId(configuration.moduleId());
        mappingEntity.setCode("import_" + UUID.randomUUID().toString().replace("-", ""));
        mappingEntity.setName(input.mappingName() == null || input.mappingName().isBlank()
                ? source.originalName() + " 字段映射" : input.mappingName().strip());
        mappingEntity.setMappingType("IMPORT");
        mappingEntity.setColumnMappingJson(json(mapping));
        mappingEntity.setValidationPolicyJson(json(Map.of("maximumRows", MAX_ROWS, "previewOnly", true)));
        mappingEntity.setConflictPolicyJson(json(Map.of("recordNumber", conflictPolicy)));
        mappingEntity.setStatus("ACTIVE");
        mappingEntity.setCreatedByMemberId(context.memberId());
        mappingEntity.setVersion(0);
        mappings.insert(mappingEntity);

        ExchangeImportBatch batch = new ExchangeImportBatch();
        batch.setSystemId(context.systemId());
        batch.setTenantId(context.tenantId());
        batch.setModuleId(configuration.moduleId());
        batch.setMappingId(mappingEntity.getId());
        batch.setSourceFileId(source.id());
        batch.setMode("DRY_RUN");
        batch.setStatus("PREVIEWING");
        batch.setTotalRows((long) csv.size() - 1);
        batch.setValidRows(0L);
        batch.setSuccessRows(0L);
        batch.setFailedRows(0L);
        batch.setAuthorizationSnapshotJson(json(authorizationSnapshot(context, moduleCode)));
        batch.setSummaryJson(json(Map.of("create", 0, "update", 0, "skip", 0, "error", 0)));
        batch.setCreatedByMemberId(context.memberId());
        batch.setVersion(0);
        batches.insert(batch);

        Map<String, Integer> counts = new LinkedHashMap<>(Map.of("create", 0, "update", 0, "skip", 0, "error", 0));
        for (int index = 1; index < csv.size(); index++) {
            ExchangeImportRow row = previewRow(context, moduleCode, batch.getId(), index + 1L, headers,
                    csv.get(index), mapping, targets, conflictPolicy, traceId);
            rows.insert(row);
            String key = switch (row.getStatus()) {
                case "READY" -> state(row).get("operation").toString().toLowerCase(Locale.ROOT);
                case "SKIP" -> "skip";
                default -> "error";
            };
            counts.computeIfPresent(key, (ignored, value) -> value + 1);
        }
        batch.setValidRows((long) counts.get("create") + counts.get("update") + counts.get("skip"));
        batch.setFailedRows((long) counts.get("error"));
        batch.setStatus("PREVIEWED");
        batch.setSummaryJson(json(counts));
        batches.updateById(batch);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "MODULE_IMPORT_PREVIEWED", "IMPORT_BATCH", batch.getId().toString(), "SUCCESS",
                authorizationSnapshot(context, moduleCode), Map.of("moduleCode", moduleCode, "sourceFileId", source.id(),
                        "totalRows", batch.getTotalRows(), "counts", counts, "recordWrites", 0));
        return view(context, moduleCode, batch.getId(), traceId);
    }

    @Transactional
    public ImportModels.ImportBatchView queue(
            AuthenticatedContext context, String moduleCode, Long batchId, Integer version, String traceId) {
        requireImport(context, moduleCode, traceId);
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        if (!"PREVIEWED".equals(batch.getStatus())) throw conflict("IMPORT_BATCH_NOT_PREVIEWED", "当前批次不能执行");
        if (!Objects.equals(version, batch.getVersion())) throw conflict("IMPORT_BATCH_VERSION_CONFLICT", "导入批次已变化，请刷新");
        if (batch.getValidRows() == 0) throw conflict("IMPORT_BATCH_HAS_NO_VALID_ROWS", "预演没有可执行行");
        batch.setMode("EXECUTE");
        batch.setStatus("QUEUED");
        batch.setStartedAt(null);
        batch.setFinishedAt(null);
        if (batches.updateById(batch) != 1) throw conflict("IMPORT_BATCH_VERSION_CONFLICT", "导入批次已变化，请刷新");
        return view(context, moduleCode, batchId, traceId);
    }

    @Transactional
    public void attachJob(AuthenticatedContext context, String moduleCode, Long batchId, Long jobId) {
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        if (Objects.equals(batch.getJobId(), jobId)) return;
        if (!"QUEUED".equals(batch.getStatus())) throw conflict("IMPORT_BATCH_NOT_QUEUED", "导入批次不再等待执行");
        batch.setJobId(jobId);
        batches.updateById(batch);
    }

    @Transactional
    public void resetQueue(AuthenticatedContext context, String moduleCode, Long batchId) {
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        if ("QUEUED".equals(batch.getStatus()) && batch.getJobId() == null) {
            batch.setMode("DRY_RUN");
            batch.setStatus("PREVIEWED");
            batches.updateById(batch);
        }
    }

    public Map<String, Object> executionParameters(AuthenticatedContext context, Long batchId) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("batchId", batchId);
        parameters.put("accountId", context.accountId());
        parameters.put("platformId", context.platformId());
        parameters.put("memberId", context.memberId());
        parameters.put("tenantMemberId", context.tenantMemberId());
        parameters.put("username", context.username());
        parameters.put("displayName", context.displayName());
        parameters.put("mfaLevel", context.mfaLevel());
        parameters.put("roleIds", context.roleIds());
        parameters.put("permissions", context.permissions());
        parameters.put("dataScopes", context.dataScopes());
        return parameters;
    }

    public long estimateRows(Object batchId) {
        if (batchId == null) return 0;
        ExchangeImportBatch batch = batches.selectById(Long.valueOf(batchId.toString()));
        return batch == null ? 0 : batch.getValidRows();
    }

    public Map<String, Object> executeQueued(
            Map<String, Object> parameters, BackgroundJobExecution execution) {
        AuthenticatedContext context = executionContext(parameters);
        long batchId = Long.parseLong(parameters.get("batchId").toString());
        ExchangeImportBatch batch = requireBatchById(context, batchId);
        if (!Set.of("QUEUED", "RUNNING").contains(batch.getStatus())) {
            throw new IllegalStateException("Import batch is not queued");
        }
        batch.setJobId(execution.jobId());
        batch.setStatus("RUNNING");
        if (batch.getStartedAt() == null) batch.setStartedAt(LocalDateTime.now());
        batches.updateById(batch);
        String moduleCode = String.valueOf(parseObject(batch.getAuthorizationSnapshotJson()).get("moduleCode"));
        List<ExchangeImportRow> items = batchRows(batchId);
        long processed = 0;
        long succeeded = items.stream().filter(item -> "SUCCEEDED".equals(item.getStatus())).count();
        long failed = items.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        for (ExchangeImportRow row : items) {
            if (!Set.of("READY", "SKIP").contains(row.getStatus())) continue;
            processed++;
            if ("SKIP".equals(row.getStatus())) {
                row.setStatus("SKIPPED");
                rows.updateById(row);
                execution.itemSucceeded("row-" + row.getRowNumber(), row.getRowNumber(), Map.of("operation", "SKIP"),
                        processed, batch.getValidRows());
                continue;
            }
            try {
                Map<String, Object> state = state(row);
                String operation = state.get("operation").toString();
                CreateRuntimeRecordRequest payload = objectMapper.convertValue(state.get("payload"),
                        CreateRuntimeRecordRequest.class);
                RuntimeRecordView result;
                if ("CREATE".equals(operation)) {
                    result = runtimeDataService.create(context, moduleCode, payload,
                            "import-" + batchId + "-row-" + row.getRowNumber());
                } else {
                    int expected = ((Number) state.get("expectedRecordVersion")).intValue();
                    result = runtimeDataService.update(context, moduleCode, row.getTargetRecordId(),
                            new UpdateRuntimeRecordRequest(payload.title(), payload.recordNumber(), payload.status(),
                                    payload.ownerMemberId(), payload.departmentId(), payload.participantMemberIds(),
                                    payload.fields(), expected),
                            "import-" + batchId + "-row-" + row.getRowNumber());
                }
                state.put("appliedRecordVersion", result.version());
                row.setTargetRecordId(result.id());
                row.setNormalizedJson(json(state));
                row.setStatus("SUCCEEDED");
                row.setErrorJson(null);
                rows.updateById(row);
                succeeded++;
                execution.itemSucceeded("row-" + row.getRowNumber(), row.getRowNumber(),
                        Map.of("operation", operation, "recordId", result.id(), "recordVersion", result.version()),
                        processed, batch.getValidRows());
            } catch (RuntimeException exception) {
                row.setStatus("FAILED");
                row.setErrorJson(json(error(exception, null)));
                rows.updateById(row);
                failed++;
                execution.itemFailed("row-" + row.getRowNumber(), row.getRowNumber(), errorCode(exception),
                        exception.getMessage(), processed, batch.getValidRows());
            }
        }
        batch = batches.selectById(batchId);
        batch.setSuccessRows(succeeded);
        batch.setFailedRows(failed + batchRows(batchId).stream().filter(item -> "ERROR".equals(item.getStatus())).count());
        batch.setStatus(failed > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        batch.setFinishedAt(LocalDateTime.now());
        Map<String, Object> summary = new LinkedHashMap<>(parseObject(batch.getSummaryJson()));
        summary.put("executed", succeeded + failed);
        summary.put("succeeded", succeeded);
        summary.put("failed", failed);
        summary.put("jobId", execution.jobId());
        batch.setSummaryJson(json(summary));
        batches.updateById(batch);
        auditRecorder.record("import-job-" + execution.jobId(), context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "MODULE_IMPORT_COMPLETED", "IMPORT_BATCH", String.valueOf(batchId), batch.getStatus(),
                Map.of("moduleCode", moduleCode, "successRows", succeeded, "failedRows", failed,
                        "jobId", execution.jobId()));
        return summary;
    }

    public ImportModels.ImportBatchView detail(
            AuthenticatedContext context, String moduleCode, Long batchId, String traceId) {
        requireImport(context, moduleCode, traceId);
        return view(context, moduleCode, batchId, traceId);
    }

    public List<ImportModels.ImportBatchView> list(
            AuthenticatedContext context, String moduleCode, String traceId) {
        RuntimeModuleConfiguration configuration = requireImport(context, moduleCode, traceId);
        return batches.selectList(Wrappers.<ExchangeImportBatch>lambdaQuery()
                        .eq(ExchangeImportBatch::getSystemId, context.systemId())
                        .eq(ExchangeImportBatch::getTenantId, context.tenantId())
                        .eq(ExchangeImportBatch::getModuleId, configuration.moduleId())
                        .orderByDesc(ExchangeImportBatch::getId).last("limit 20"))
                .stream().map(batch -> view(context, moduleCode, batch.getId(), traceId)).toList();
    }

    public ImportModels.ImportBatchView rollback(
            AuthenticatedContext context,
            String moduleCode,
            Long batchId,
            ImportModels.RollbackRequest input,
            String traceId) {
        requireImport(context, moduleCode, traceId);
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        if (!Set.of("COMPLETED", "COMPLETED_WITH_ERRORS", "ROLLBACK_PARTIAL").contains(batch.getStatus())) {
            throw conflict("IMPORT_BATCH_NOT_ROLLBACKABLE", "当前批次不能回滚");
        }
        long rolledBack = 0;
        long conflicts = 0;
        for (ExchangeImportRow row : batchRows(batchId)) {
            if (!"SUCCEEDED".equals(row.getStatus())) continue;
            Map<String, Object> state = state(row);
            try {
                String operation = state.get("operation").toString();
                int appliedVersion = ((Number) state.get("appliedRecordVersion")).intValue();
                RuntimeRecordView current = runtimeDataService.detail(context, moduleCode, row.getTargetRecordId(),
                        traceId + "-rollback-read-" + row.getRowNumber());
                if (!Objects.equals(appliedVersion, current.version())) {
                    throw conflict("IMPORT_ROLLBACK_VERSION_CONFLICT", "记录已在导入后被修改，不能覆盖后续变更");
                }
                if ("CREATE".equals(operation)) {
                    runtimeDataService.delete(context, moduleCode, row.getTargetRecordId(),
                            new RecordLifecycleRequest(reason(input), current.version()),
                            traceId + "-rollback-create-" + row.getRowNumber());
                } else {
                    CreateRuntimeRecordRequest before = objectMapper.convertValue(state.get("before"),
                            CreateRuntimeRecordRequest.class);
                    runtimeDataService.update(context, moduleCode, row.getTargetRecordId(),
                            new UpdateRuntimeRecordRequest(before.title(), before.recordNumber(), before.status(),
                                    before.ownerMemberId(), before.departmentId(), before.participantMemberIds(),
                                    before.fields(), current.version()),
                            traceId + "-rollback-update-" + row.getRowNumber());
                }
                state.put("rollbackStatus", "ROLLED_BACK");
                row.setNormalizedJson(json(state));
                row.setStatus("ROLLED_BACK");
                row.setErrorJson(null);
                rows.updateById(row);
                rolledBack++;
            } catch (RuntimeException exception) {
                Map<String, Object> failure = error(exception, "ROLLBACK_CONFLICT");
                row.setStatus("ROLLBACK_CONFLICT");
                row.setErrorJson(json(failure));
                rows.updateById(row);
                conflicts++;
            }
        }
        batch = batches.selectById(batchId);
        batch.setStatus(conflicts == 0 ? "ROLLED_BACK" : "ROLLBACK_PARTIAL");
        Map<String, Object> summary = new LinkedHashMap<>(parseObject(batch.getSummaryJson()));
        summary.put("rollbackStatus", batch.getStatus());
        summary.put("rolledBackRows", rolledBack);
        summary.put("rollbackConflicts", conflicts);
        batch.setSummaryJson(json(summary));
        batch.setFinishedAt(LocalDateTime.now());
        batches.updateById(batch);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_IMPORT_ROLLED_BACK", "IMPORT_BATCH", batchId.toString(), batch.getStatus(),
                Map.of("moduleCode", moduleCode, "rolledBackRows", rolledBack, "conflicts", conflicts,
                        "reason", reason(input)));
        return view(context, moduleCode, batchId, traceId);
    }

    public byte[] errorCsv(AuthenticatedContext context, String moduleCode, Long batchId, String traceId) {
        requireImport(context, moduleCode, traceId);
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        StringBuilder csv = new StringBuilder("rowNumber,status,errorCode,errorMessage\n");
        batchRows(batch.getId()).stream()
                .filter(row -> Set.of("ERROR", "FAILED", "ROLLBACK_CONFLICT").contains(row.getStatus()))
                .forEach(row -> {
                    Map<String, Object> error = parseObject(row.getErrorJson());
                    csv.append(row.getRowNumber()).append(',').append(csvCell(row.getStatus())).append(',')
                            .append(csvCell(String.valueOf(error.getOrDefault("code", "IMPORT_ROW_FAILED")))).append(',')
                            .append(csvCell(String.valueOf(error.getOrDefault("message", "导入行失败")))).append('\n');
                });
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ExchangeImportRow previewRow(
            AuthenticatedContext context, String moduleCode, Long batchId, long rowNumber,
            List<String> headers, List<String> values, Map<String, String> mapping,
            Map<String, ImportModels.TemplateColumn> targets, String conflictPolicy, String traceId) {
        ExchangeImportRow row = new ExchangeImportRow();
        row.setBatchId(batchId);
        row.setRowNumber(rowNumber);
        Map<String, String> raw = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            raw.put(headers.get(index), index < values.size() ? values.get(index) : "");
        }
        row.setRawJson(json(raw));
        row.setVersion(0);
        try {
            Map<String, JsonNode> submitted = new LinkedHashMap<>();
            for (Map.Entry<String, String> item : mapping.entrySet()) {
                ImportModels.TemplateColumn target = targets.get(item.getValue());
                submitted.put(item.getValue(), importValue(raw.getOrDefault(item.getKey(), ""), target));
            }
            String title = text(submitted.remove("title"));
            String recordNumber = nullableText(submitted.remove("recordNumber"));
            String status = nullableText(submitted.remove("status"));
            if (title == null || title.isBlank()) throw invalid("IMPORT_TITLE_REQUIRED", "标题不能为空");
            RuntimeRecordView existing = existing(context, moduleCode, recordNumber, traceId);
            String operation = existing == null ? "CREATE" : switch (conflictPolicy) {
                case "UPDATE" -> "UPDATE";
                case "SKIP" -> "SKIP";
                default -> throw conflict("IMPORT_RECORD_CONFLICT", "业务编号已存在：" + recordNumber);
            };
            if ("SKIP".equals(operation)) {
                row.setStatus("SKIP");
                row.setTargetRecordId(existing.id());
                row.setNormalizedJson(json(Map.of("operation", "SKIP", "reason", "RECORD_NUMBER_EXISTS")));
                return row;
            }
            CreateRuntimeRecordRequest request = new CreateRuntimeRecordRequest(title, recordNumber, status,
                    existing == null ? context.memberId() : existing.ownerMemberId(),
                    existing == null ? null : existing.departmentId(),
                    existing == null ? List.of() : existing.participantMemberIds(), submitted);
            RuntimeImportValidation validation = runtimeDataService.validateImport(context, moduleCode,
                    existing == null ? null : existing.id(), request,
                    existing == null ? null : existing.version(), traceId + "-preview-row-" + rowNumber);
            CreateRuntimeRecordRequest normalized = new CreateRuntimeRecordRequest(request.title(), request.recordNumber(),
                    request.status(), request.ownerMemberId(), request.departmentId(), request.participantMemberIds(),
                    validation.normalizedFields());
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("operation", operation);
            state.put("payload", normalized);
            if (validation.beforeState() != null) state.put("before", validation.beforeState());
            if (validation.recordVersion() != null) state.put("expectedRecordVersion", validation.recordVersion());
            row.setStatus("READY");
            row.setTargetRecordId(existing == null ? null : existing.id());
            row.setNormalizedJson(json(state));
        } catch (RuntimeException exception) {
            row.setStatus("ERROR");
            row.setErrorJson(json(error(exception, null)));
        }
        return row;
    }

    private RuntimeRecordView existing(
            AuthenticatedContext context, String moduleCode, String recordNumber, String traceId) {
        if (recordNumber == null || recordNumber.isBlank()) return null;
        String filters = json(List.of(Map.of("fieldCode", "recordNumber", "operator", "EQ", "value", recordNumber)));
        RuntimeRecordList result = runtimeDataService.list(context, moduleCode, "ACTIVE", "OWN", "", filters,
                "updatedAt", "DESC", 1, 2, traceId + "-conflict-check");
        return result.records().stream().filter(record -> recordNumber.equals(record.recordNumber())).findFirst().orElse(null);
    }

    private RuntimeModuleConfiguration requireImport(
            AuthenticatedContext context, String moduleCode, String traceId) {
        if (!permissionChecker.allows(context, "MODULE", moduleCode, "IMPORT")) {
            auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE:" + moduleCode + ":IMPORT",
                    Map.of("roleIds", context.roleIds(), "permissions", context.permissions()));
            throw forbidden("IMPORT_PERMISSION_DENIED", "没有该模块的数据导入权限");
        }
        RuntimeModuleConfiguration configuration = publicationService.runtime(context, moduleCode, traceId);
        boolean publishedAction = false;
        for (JsonNode action : configuration.configuration().path("actions")) {
            if ("IMPORT".equals(action.path("code").asText())) publishedAction = true;
        }
        if (!publishedAction) throw invalid("IMPORT_ACTION_NOT_PUBLISHED", "模块当前发布版本未启用导入动作");
        return configuration;
    }

    private ExchangeImportBatch requireBatch(AuthenticatedContext context, String moduleCode, Long batchId) {
        ExchangeImportBatch batch = requireBatchById(context, batchId);
        RuntimeModuleConfiguration configuration = publicationService.published(context, moduleCode);
        if (!Objects.equals(batch.getModuleId(), configuration.moduleId())) {
            throw notFound("IMPORT_BATCH_NOT_FOUND", "导入批次不存在");
        }
        return batch;
    }

    private ExchangeImportBatch requireBatchById(AuthenticatedContext context, Long batchId) {
        ExchangeImportBatch batch = batches.selectById(batchId);
        if (batch == null || !Objects.equals(batch.getSystemId(), context.systemId())
                || !Objects.equals(batch.getTenantId(), context.tenantId())) {
            throw notFound("IMPORT_BATCH_NOT_FOUND", "导入批次不存在");
        }
        return batch;
    }

    private ImportModels.ImportBatchView view(
            AuthenticatedContext context, String moduleCode, Long batchId, String traceId) {
        ExchangeImportBatch batch = requireBatch(context, moduleCode, batchId);
        ExchangeMapping mapping = mappings.selectById(batch.getMappingId());
        List<ImportModels.ImportRowView> rowViews = batchRows(batchId).stream().map(this::rowView).toList();
        return new ImportModels.ImportBatchView(batch.getId(), batch.getSystemId(), batch.getTenantId(),
                batch.getModuleId(), moduleCode, batch.getMappingId(), batch.getSourceFileId(), batch.getJobId(),
                batch.getMode(), batch.getStatus(), value(batch.getTotalRows()), value(batch.getValidRows()),
                value(batch.getSuccessRows()), value(batch.getFailedRows()),
                mapping == null ? Map.of() : parseStringMap(mapping.getColumnMappingJson()),
                mapping == null ? "ERROR" : String.valueOf(parseObject(mapping.getConflictPolicyJson())
                        .getOrDefault("recordNumber", "ERROR")),
                parseObject(batch.getSummaryJson()), rowViews, batch.getCreatedAt(), batch.getStartedAt(),
                batch.getFinishedAt(), batch.getVersion());
    }

    private ImportModels.ImportRowView rowView(ExchangeImportRow row) {
        Map<String, Object> state = state(row);
        Map<String, Object> error = parseObject(row.getErrorJson());
        return new ImportModels.ImportRowView(row.getId(), row.getRowNumber(),
                String.valueOf(state.getOrDefault("operation", row.getStatus())), row.getStatus(), row.getTargetRecordId(),
                integer(state.get("expectedRecordVersion")), integer(state.get("appliedRecordVersion")),
                parseObject(row.getRawJson()), state, nullable(error.get("code")), nullable(error.get("message")),
                nullable(state.getOrDefault("rollbackStatus", error.get("rollbackStatus"))), row.getVersion());
    }

    private List<ExchangeImportRow> batchRows(Long batchId) {
        return rows.selectList(Wrappers.<ExchangeImportRow>lambdaQuery()
                .eq(ExchangeImportRow::getBatchId, batchId).orderByAsc(ExchangeImportRow::getRowNumber));
    }

    private Map<String, Object> authorizationSnapshot(AuthenticatedContext context, String moduleCode) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("moduleCode", moduleCode);
        snapshot.put("accountId", context.accountId());
        snapshot.put("memberId", context.memberId());
        snapshot.put("tenantMemberId", context.tenantMemberId());
        snapshot.put("roleIds", context.roleIds());
        snapshot.put("permissions", context.permissions());
        snapshot.put("dataScopes", context.dataScopes());
        return snapshot;
    }

    private AuthenticatedContext executionContext(Map<String, Object> parameters) {
        return new AuthenticatedContext(null, longValue(parameters.get("accountId")),
                longValue(parameters.get("platformId")), longValue(parameters.get("systemId")),
                longValue(parameters.get("tenantId")), longValue(parameters.get("memberId")),
                longValue(parameters.get("tenantMemberId")), String.valueOf(parameters.getOrDefault("username", "job")),
                String.valueOf(parameters.getOrDefault("displayName", "后台导入")),
                String.valueOf(parameters.getOrDefault("mfaLevel", "PASSWORD")),
                objectMapper.convertValue(parameters.getOrDefault("roleIds", List.of()), new TypeReference<List<Long>>() { }),
                objectMapper.convertValue(parameters.getOrDefault("permissions", List.of()),
                        new TypeReference<List<PermissionGrant>>() { }),
                objectMapper.convertValue(parameters.getOrDefault("dataScopes", Map.of()),
                        new TypeReference<Map<String, DataScopeExpression>>() { }));
    }

    private Map<String, String> normalizedMapping(
            List<String> headers, Map<String, String> requested, Set<String> targets) {
        Map<String, String> result = new LinkedHashMap<>();
        if (requested != null) requested.forEach((source, target) -> {
            String cleanSource = source == null ? "" : source.strip();
            String cleanTarget = target == null ? "" : target.strip();
            if (!headers.contains(cleanSource)) throw invalid("IMPORT_MAPPING_SOURCE_INVALID", "映射源列不存在：" + cleanSource);
            if (!targets.contains(cleanTarget)) throw invalid("IMPORT_MAPPING_TARGET_INVALID", "映射目标字段不存在：" + cleanTarget);
            if (result.containsValue(cleanTarget)) throw invalid("IMPORT_MAPPING_TARGET_DUPLICATE", "目标字段不能重复映射：" + cleanTarget);
            result.put(cleanSource, cleanTarget);
        });
        if (result.isEmpty()) {
            headers.stream().filter(targets::contains).forEach(header -> result.put(header, header));
        }
        return result;
    }

    private JsonNode importValue(String raw, ImportModels.TemplateColumn target) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) return NullNode.instance;
        return switch (target.fieldType()) {
            case "BOOLEAN" -> {
                if (Set.of("true", "1", "是", "yes").contains(value.toLowerCase(Locale.ROOT))) yield BooleanNode.TRUE;
                if (Set.of("false", "0", "否", "no").contains(value.toLowerCase(Locale.ROOT))) yield BooleanNode.FALSE;
                yield TextNode.valueOf(value);
            }
            case "MEMBER", "DEPARTMENT", "REFERENCE" -> {
                try { yield LongNode.valueOf(Long.parseLong(value)); }
                catch (NumberFormatException ignored) { yield TextNode.valueOf(value); }
            }
            case "MULTI_SELECT", "TAG" -> {
                ArrayNode array = objectMapper.createArrayNode();
                for (String item : value.split("\\|")) if (!item.isBlank()) array.add(item.strip());
                yield array;
            }
            default -> TextNode.valueOf(value);
        };
    }

    private List<List<String>> parseCsv(String content) {
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                record.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < normalized.length() && normalized.charAt(index + 1) == '\n') index++;
                record.add(cell.toString());
                cell.setLength(0);
                if (record.stream().anyMatch(value -> !value.isBlank())) records.add(List.copyOf(record));
                record.clear();
            } else cell.append(current);
        }
        if (quoted) throw invalid("IMPORT_CSV_INVALID", "CSV 引号没有闭合");
        record.add(cell.toString());
        if (record.stream().anyMatch(value -> !value.isBlank())) records.add(List.copyOf(record));
        return records;
    }

    private String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private String nullableText(JsonNode value) {
        String text = text(value);
        return text == null || text.isBlank() ? null : text;
    }

    private Map<String, Object> state(ExchangeImportRow row) {
        return parseObject(row.getNormalizedJson());
    }

    private Map<String, Object> error(RuntimeException exception, String rollbackStatus) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", errorCode(exception));
        detail.put("message", exception.getMessage() == null ? "导入行处理失败" : exception.getMessage());
        if (rollbackStatus != null) detail.put("rollbackStatus", rollbackStatus);
        return detail;
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof DomainException domain ? domain.code() : "IMPORT_ROW_FAILED";
    }

    private String reason(ImportModels.RollbackRequest input) {
        return input == null || input.reason() == null || input.reason().isBlank()
                ? "撤销本次导入批次" : input.reason().strip();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize import state", exception); }
    }

    private Map<String, Object> parseObject(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, OBJECT_MAP); }
        catch (JsonProcessingException exception) { return Map.of("unreadable", true); }
    }

    private Map<String, String> parseStringMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, STRING_MAP); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        return text.contains(",") || text.contains("\"") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String nullable(Object value) {
        return value == null ? null : value.toString();
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(code, message, HttpStatus.NOT_FOUND);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
