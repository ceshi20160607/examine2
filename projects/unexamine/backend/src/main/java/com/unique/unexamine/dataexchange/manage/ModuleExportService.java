package com.unique.unexamine.dataexchange.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobExecution;
import com.unique.unexamine.dataexchange.base.entity.ExchangeExportBatch;
import com.unique.unexamine.dataexchange.base.service.ExchangeExportBatchBaseService;
import com.unique.unexamine.file.manage.FileModels;
import com.unique.unexamine.file.manage.FileStorageService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
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

@Service
public class ModuleExportService {
    private static final List<FieldDefinition> BUILT_INS = List.of(
            new FieldDefinition("recordNumber", "记录编号", "TEXT", true, null),
            new FieldDefinition("title", "标题", "TEXT", true, null),
            new FieldDefinition("status", "状态", "TEXT", true, null),
            new FieldDefinition("ownerMemberId", "负责人", "MEMBER", true, null),
            new FieldDefinition("departmentId", "部门", "DEPARTMENT", true, null),
            new FieldDefinition("dataTenantName", "数据归属", "TEXT", true, null),
            new FieldDefinition("createdAt", "创建时间", "DATETIME", true, null),
            new FieldDefinition("updatedAt", "更新时间", "DATETIME", true, null));

    private final ExchangeExportBatchBaseService batches;
    private final ModulePublicationService publicationService;
    private final RuntimeDataService runtimeDataService;
    private final BusinessRecordBaseService recordService;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final PermissionChecker permissionChecker;
    private final PermissionResolver permissionResolver;
    private final FileStorageService fileStorageService;
    private final MessageService messageService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final long maximumRows;

    public ModuleExportService(
            ExchangeExportBatchBaseService batches,
            ModulePublicationService publicationService,
            RuntimeDataService runtimeDataService,
            BusinessRecordBaseService recordService,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            PermissionChecker permissionChecker,
            PermissionResolver permissionResolver,
            FileStorageService fileStorageService,
            MessageService messageService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            @Value("${unexamine.exports.max-rows:5000}") long maximumRows) {
        this.batches = batches;
        this.publicationService = publicationService;
        this.runtimeDataService = runtimeDataService;
        this.recordService = recordService;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.permissionChecker = permissionChecker;
        this.permissionResolver = permissionResolver;
        this.fileStorageService = fileStorageService;
        this.messageService = messageService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.maximumRows = Math.max(1, maximumRows);
    }

    @Transactional(readOnly = true)
    public ExportModels.EstimateView estimate(
            AuthenticatedContext context, String moduleCode, ExportModels.ExportQuery input, String traceId) {
        ExportPreparation preparation = prepare(context, moduleCode, input, traceId, false);
        return preparation.estimate();
    }

    @Transactional
    public ExportModels.ExportBatchView queue(
            AuthenticatedContext context, String moduleCode, ExportModels.ExportQuery input, String traceId) {
        ExportPreparation preparation = prepare(context, moduleCode, input, traceId, true);
        if (!preparation.estimate().withinQuota()) {
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE_EXPORT_REJECTED", "MODULE", moduleCode,
                    "EXPORT_ROW_QUOTA_EXCEEDED", Map.of("estimatedRows", preparation.records().size(),
                            "maximumRows", maximumRows));
            throw invalid("EXPORT_ROW_QUOTA_EXCEEDED", "预计导出行数超过当前配额 " + maximumRows + " 行");
        }
        ExchangeExportBatch batch = new ExchangeExportBatch();
        batch.setSystemId(context.systemId());
        batch.setTenantId(context.tenantId());
        batch.setModuleId(preparation.configuration().moduleId());
        Map<String, Object> filterSnapshot = querySnapshot(input);
        filterSnapshot.put("authorizedRecordIds", preparation.records().stream().map(RuntimeRecordView::id).toList());
        filterSnapshot.put("estimatedAt", LocalDateTime.now().toString());
        batch.setFilterSnapshotJson(json(filterSnapshot));
        batch.setAuthorizationSnapshotJson(json(authorizationSnapshot(context, moduleCode, preparation.fields())));
        batch.setSelectedFieldsJson(json(preparation.fields().stream().map(FieldDefinition::code).toList()));
        batch.setStatus("QUEUED");
        batch.setTotalRows((long) preparation.records().size());
        batch.setExportedRows(0L);
        batch.setCreatedByMemberId(context.memberId());
        batch.setVersion(0);
        batches.insert(batch);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "MODULE_EXPORT_QUEUED", "EXPORT_BATCH", batch.getId().toString(), "QUEUED",
                authorizationSnapshot(context, moduleCode, preparation.fields()), Map.of(
                        "moduleCode", moduleCode, "estimatedRows", batch.getTotalRows(),
                        "selectedFields", preparation.fields().stream().map(FieldDefinition::code).toList(),
                        "scope", preparation.estimate().scope()));
        return view(moduleCode, batch);
    }

    @Transactional
    public void attachJob(AuthenticatedContext context, String moduleCode, Long batchId, Long jobId) {
        ExchangeExportBatch batch = requireBatch(context, moduleCode, batchId);
        if (Objects.equals(batch.getJobId(), jobId)) return;
        if (!"QUEUED".equals(batch.getStatus())) throw conflict("EXPORT_BATCH_NOT_QUEUED", "导出批次不再等待执行");
        batch.setJobId(jobId);
        batches.updateById(batch);
    }

    @Transactional
    public void failQueue(
            AuthenticatedContext context, String moduleCode, Long batchId, String message) {
        ExchangeExportBatch batch = requireBatch(context, moduleCode, batchId);
        if ("QUEUED".equals(batch.getStatus()) && batch.getJobId() == null) {
            batch.setStatus("FAILED");
            batch.setErrorMessage(truncate(message));
            batch.setFinishedAt(LocalDateTime.now());
            batches.updateById(batch);
        }
    }

    public Map<String, Object> executionParameters(AuthenticatedContext context, Long batchId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("accountId", context.accountId());
        result.put("platformId", context.platformId());
        result.put("memberId", context.memberId());
        result.put("tenantMemberId", context.tenantMemberId());
        result.put("username", context.username());
        result.put("displayName", context.displayName());
        result.put("mfaLevel", context.mfaLevel());
        return result;
    }

    public long estimateRows(Object batchId) {
        if (batchId == null) return 0;
        ExchangeExportBatch batch = batches.selectById(Long.valueOf(batchId.toString()));
        return batch == null || batch.getTotalRows() == null ? 0 : batch.getTotalRows();
    }

    public Map<String, Object> executeQueued(
            Map<String, Object> parameters, BackgroundJobExecution execution) {
        long batchId = Long.parseLong(String.valueOf(parameters.get("batchId")));
        ExchangeExportBatch batch = batches.selectById(batchId);
        if (batch == null || !Set.of("QUEUED", "RUNNING").contains(batch.getStatus())) {
            throw new IllegalStateException("Export batch is not queued");
        }
        AuthenticatedContext context = freshContext(parameters, batch);
        Map<String, Object> authorization = parseObject(batch.getAuthorizationSnapshotJson());
        String moduleCode = String.valueOf(authorization.get("moduleCode"));
        try {
            requireExport(context, moduleCode, "export-job-" + execution.jobId());
            batch.setJobId(execution.jobId());
            batch.setStatus("RUNNING");
            if (batch.getStartedAt() == null) batch.setStartedAt(LocalDateTime.now());
            batch.setErrorMessage(null);
            batches.updateById(batch);

            Map<String, Object> snapshot = parseObject(batch.getFilterSnapshotJson());
            ExportModels.ExportQuery query = queryFromSnapshot(snapshot,
                    parseStringList(batch.getSelectedFieldsJson()));
            RuntimeModuleConfiguration configuration = publicationService.published(context, moduleCode);
            List<FieldDefinition> available = availableFields(context, moduleCode, configuration);
            Map<String, FieldDefinition> byCode = new LinkedHashMap<>();
            available.forEach(field -> byCode.put(field.code(), field));
            List<FieldDefinition> selectedFields = parseStringList(batch.getSelectedFieldsJson()).stream()
                    .map(byCode::get).filter(Objects::nonNull).toList();
            if (selectedFields.size() != parseStringList(batch.getSelectedFieldsJson()).size()) {
                throw forbidden("EXPORT_FIELD_PERMISSION_REVOKED", "导出字段权限在任务执行前已失效");
            }
            Set<Long> authorizedAtSubmission = new LinkedHashSet<>(longList(snapshot.get("authorizedRecordIds")));
            ExportModels.ExportQuery executionQuery = new ExportModels.ExportQuery(query.lifecycleState(),
                    query.tenantScope(), query.search(), query.filters(), query.sortField(), query.sortDirection(),
                    authorizedAtSubmission.stream().toList(), query.selectedFields());
            List<RuntimeRecordView> current = records(
                    context, moduleCode, executionQuery, trace(execution), maximumRows + 1);
            List<RuntimeRecordView> exported = current.stream()
                    .filter(record -> authorizedAtSubmission.contains(record.id())).toList();
            if (exported.size() > maximumRows) {
                throw invalid("EXPORT_ROW_QUOTA_EXCEEDED", "任务执行时的导出行数超过配额");
            }
            List<String> snapshotMasked = stringList(authorization.get("maskedFields"));
            byte[] csv = csv(context, selectedFields, snapshotMasked, exported, batchId, execution);
            FileModels.FileView file = fileStorageService.storeGenerated(context,
                    moduleCode + "-export-" + batchId + ".csv", "text/csv;charset=UTF-8", csv,
                    "module_export", "export-job-" + execution.jobId());
            batch = batches.selectById(batchId);
            batch.setStatus("COMPLETED");
            batch.setExportedRows((long) exported.size());
            batch.setResultFileId(file.id());
            batch.setFinishedAt(LocalDateTime.now());
            batch.setErrorMessage(null);
            batches.updateById(batch);
            auditRecorder.record("export-job-" + execution.jobId(), context.accountId(), context.systemId(),
                    context.tenantId(), context.memberId(), "MODULE_EXPORT_COMPLETED", "EXPORT_BATCH",
                    String.valueOf(batchId), "COMPLETED", Map.of("moduleCode", moduleCode,
                            "exportedRows", exported.size(), "fileId", file.id(), "jobId", execution.jobId()));
            notifyResult(context, batchId, moduleCode, true, exported.size(), null,
                    "export-job-" + execution.jobId() + "-message");
            return Map.of("batchId", batchId, "fileId", file.id(), "exportedRows", exported.size());
        } catch (RuntimeException exception) {
            ExchangeExportBatch failed = batches.selectById(batchId);
            failed.setStatus("FAILED");
            failed.setResultFileId(null);
            failed.setErrorMessage(truncate(exception.getMessage()));
            failed.setFinishedAt(LocalDateTime.now());
            batches.updateById(failed);
            auditRecorder.recordFailure("export-job-" + execution.jobId(), context.accountId(), context.systemId(),
                    context.tenantId(), context.memberId(), "MODULE_EXPORT_FAILED", "EXPORT_BATCH",
                    String.valueOf(batchId), errorCode(exception), Map.of("moduleCode", moduleCode,
                            "jobId", execution.jobId(), "message", String.valueOf(exception.getMessage())));
            notifyResult(context, batchId, moduleCode, false, value(failed.getExportedRows()),
                    exception.getMessage(), "export-job-" + execution.jobId() + "-failure-message");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ExportModels.ExportBatchView> list(
            AuthenticatedContext context, String moduleCode, String traceId) {
        RuntimeModuleConfiguration configuration = requireExport(context, moduleCode, traceId);
        return batches.selectList(Wrappers.<ExchangeExportBatch>lambdaQuery()
                        .eq(ExchangeExportBatch::getSystemId, context.systemId())
                        .eq(ExchangeExportBatch::getTenantId, context.tenantId())
                        .eq(ExchangeExportBatch::getModuleId, configuration.moduleId())
                        .eq(ExchangeExportBatch::getCreatedByMemberId, context.memberId())
                        .orderByDesc(ExchangeExportBatch::getId).last("limit 20"))
                .stream().map(batch -> view(moduleCode, batch)).toList();
    }

    @Transactional(readOnly = true)
    public ExportModels.ExportBatchView detail(
            AuthenticatedContext context, String moduleCode, Long batchId, String traceId) {
        requireExport(context, moduleCode, traceId);
        return view(moduleCode, requireBatch(context, moduleCode, batchId));
    }

    public FileModels.BinaryContent download(
            AuthenticatedContext context, String moduleCode, Long batchId, String traceId) {
        requireExport(context, moduleCode, traceId);
        ExchangeExportBatch batch = requireBatch(context, moduleCode, batchId);
        if (!"COMPLETED".equals(batch.getStatus()) || batch.getResultFileId() == null) {
            throw conflict("EXPORT_FILE_NOT_READY", "导出文件尚未生成或任务已经失败");
        }
        FileModels.BinaryContent content = fileStorageService.downloadGenerated(
                context, batch.getResultFileId(), traceId);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_EXPORT_DOWNLOADED", "EXPORT_BATCH", batchId.toString(), "SUCCESS",
                Map.of("moduleCode", moduleCode, "fileId", batch.getResultFileId(),
                        "exportedRows", value(batch.getExportedRows())));
        return content;
    }

    private ExportPreparation prepare(
            AuthenticatedContext context, String moduleCode, ExportModels.ExportQuery input,
            String traceId, boolean loadAll) {
        RuntimeModuleConfiguration configuration = requireExport(context, moduleCode, traceId);
        List<FieldDefinition> available = availableFields(context, moduleCode, configuration);
        Map<String, FieldDefinition> byCode = new LinkedHashMap<>();
        available.forEach(field -> byCode.put(field.code(), field));
        List<String> requested = input.selectedFields().stream().filter(Objects::nonNull)
                .map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
        List<String> invalidFields = requested.stream().filter(code -> !byCode.containsKey(code)).toList();
        if (!invalidFields.isEmpty()) {
            throw forbidden("EXPORT_FIELD_FORBIDDEN", "导出字段不存在或无文件渠道读取权限：" + String.join("、", invalidFields));
        }
        List<FieldDefinition> selectedFields = requested.stream().map(byCode::get).toList();
        long limit = loadAll ? maximumRows + 1 : maximumRows + 1;
        List<RuntimeRecordView> records = records(context, moduleCode, input, traceId, limit);
        List<ExportModels.ExportFieldView> fieldViews = available.stream().map(FieldDefinition::view).toList();
        List<ExportModels.ExportFieldView> selectedViews = selectedFields.stream().map(FieldDefinition::view).toList();
        List<String> masked = selectedFields.stream().filter(FieldDefinition::masked).map(FieldDefinition::code).toList();
        String scope = input.selectedRecordIds() == null || input.selectedRecordIds().isEmpty() ? "CURRENT_FILTER" : "SELECTED";
        ExportModels.EstimateView estimate = new ExportModels.EstimateView(moduleCode, scope, records.size(),
                maximumRows, records.size() <= maximumRows, fieldViews, selectedViews, masked);
        return new ExportPreparation(configuration, selectedFields, records, estimate);
    }

    private List<FieldDefinition> availableFields(
            AuthenticatedContext context, String moduleCode, RuntimeModuleConfiguration configuration) {
        List<FieldDefinition> result = new ArrayList<>(BUILT_INS);
        List<String> codes = new ArrayList<>();
        configuration.configuration().path("fields").forEach(field -> codes.add(field.path("code").asText()));
        Map<String, FieldAccessDecision> listDecisions = fieldPolicyResolver.resolve(
                context, moduleCode, "LIST", "PAGE", codes);
        Map<String, FieldAccessDecision> exportDecisions = fieldPolicyResolver.resolve(
                context, moduleCode, "EXPORT", "FILE", codes);
        configuration.configuration().path("fields").forEach(field -> {
            String code = field.path("code").asText();
            FieldAccessDecision page = listDecisions.get(code);
            FieldAccessDecision file = exportDecisions.get(code);
            if (page != null && page.readable() && file != null && file.readable()) {
                String mask = stronger(page.maskStrategy(), file.maskStrategy());
                result.add(new FieldDefinition(code, field.path("name").asText(code),
                        field.path("fieldType").asText("TEXT"), false,
                        field.path("referenceModuleId").isNumber() ? field.path("referenceModuleId").longValue() : null,
                        mask));
            }
        });
        return result;
    }

    private List<RuntimeRecordView> records(
            AuthenticatedContext context, String moduleCode, ExportModels.ExportQuery input,
            String traceId, long limit) {
        String filtersJson = json(input.filters() == null ? List.of() : input.filters());
        String lifecycle = input.lifecycleState() == null ? "ACTIVE" : input.lifecycleState();
        String tenantScope = input.tenantScope() == null ? "ALL" : input.tenantScope();
        String sortField = input.sortField() == null ? "updatedAt" : input.sortField();
        String sortDirection = input.sortDirection() == null ? "DESC" : input.sortDirection();
        Set<Long> selected = input.selectedRecordIds() == null ? Set.of()
                : new LinkedHashSet<>(input.selectedRecordIds().stream().filter(Objects::nonNull).toList());
        List<RuntimeRecordView> result = new ArrayList<>();
        int page = 1;
        long total;
        do {
            RuntimeRecordList current = runtimeDataService.list(context, moduleCode, lifecycle, tenantScope,
                    input.search(), filtersJson, sortField, sortDirection, page, 200, traceId + "-page-" + page);
            total = current.total();
            current.records().stream().filter(record -> selected.isEmpty() || selected.contains(record.id()))
                    .forEach(result::add);
            if (result.size() > limit || (long) page * 200 >= total) break;
            page++;
        } while (true);
        return result.size() > limit ? result.subList(0, (int) limit + 1) : result;
    }

    private byte[] csv(
            AuthenticatedContext context, List<FieldDefinition> fields,
            List<String> snapshotMasked, List<RuntimeRecordView> records,
            Long batchId, BackgroundJobExecution execution) {
        StringBuilder result = new StringBuilder("\uFEFF");
        result.append(fields.stream().map(field -> csvCell(field.name())).collect(java.util.stream.Collectors.joining(",")))
                .append('\n');
        Set<String> submissionMasks = new LinkedHashSet<>(snapshotMasked);
        long processed = 0;
        for (RuntimeRecordView record : records) {
            List<String> values = new ArrayList<>();
            for (FieldDefinition field : fields) {
                Object value = field.builtIn() ? builtIn(record, field.code()) : record.fields().get(field.code());
                if (!field.builtIn() && submissionMasks.contains(field.code()) && value != null) {
                    value = mask(String.valueOf(jsonValue(value)), field.maskStrategy() == null ? "FULL" : field.maskStrategy());
                }
                if ("REFERENCE".equals(field.fieldType()) && value instanceof JsonNode node && node.isNumber()) {
                    BusinessRecord referenced = recordService.selectById(node.longValue());
                    value = referenced == null || !Objects.equals(referenced.getSystemId(), context.systemId())
                            ? node.asText() : referenced.getTitle();
                }
                values.add(csvCell(text(value)));
            }
            result.append(String.join(",", values)).append('\n');
            processed++;
            ExchangeExportBatch progress = batches.selectById(batchId);
            progress.setExportedRows(processed);
            batches.updateById(progress);
            execution.heartbeat(processed, records.size());
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Object builtIn(RuntimeRecordView record, String code) {
        return switch (code) {
            case "recordNumber" -> record.recordNumber();
            case "title" -> record.title();
            case "status" -> record.status();
            case "ownerMemberId" -> record.ownerMemberId();
            case "departmentId" -> record.departmentId();
            case "dataTenantName" -> record.dataTenantName();
            case "createdAt" -> record.createdAt();
            case "updatedAt" -> record.updatedAt();
            default -> null;
        };
    }

    private RuntimeModuleConfiguration requireExport(
            AuthenticatedContext context, String moduleCode, String traceId) {
        if (!permissionChecker.allows(context, "MODULE", moduleCode, "LIST")
                || !permissionChecker.allows(context, "MODULE", moduleCode, "EXPORT")) {
            auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE:" + moduleCode + ":EXPORT",
                    Map.of("roleIds", context.roleIds(), "permissions", context.permissions()));
            throw forbidden("EXPORT_PERMISSION_DENIED", "没有该模块的数据导出权限");
        }
        RuntimeModuleConfiguration configuration = publicationService.published(context, moduleCode);
        boolean publishedAction = false;
        for (JsonNode action : configuration.configuration().path("actions")) {
            if ("EXPORT".equals(action.path("code").asText())) publishedAction = true;
        }
        if (!publishedAction) throw invalid("EXPORT_ACTION_NOT_PUBLISHED", "模块当前发布版本未启用导出动作");
        return configuration;
    }

    private ExchangeExportBatch requireBatch(
            AuthenticatedContext context, String moduleCode, Long batchId) {
        ExchangeExportBatch batch = batches.selectById(batchId);
        RuntimeModuleConfiguration configuration = publicationService.published(context, moduleCode);
        if (batch == null || !Objects.equals(batch.getSystemId(), context.systemId())
                || !Objects.equals(batch.getTenantId(), context.tenantId())
                || !Objects.equals(batch.getModuleId(), configuration.moduleId())
                || !Objects.equals(batch.getCreatedByMemberId(), context.memberId())) {
            throw notFound("EXPORT_BATCH_NOT_FOUND", "导出批次不存在");
        }
        return batch;
    }

    private AuthenticatedContext freshContext(Map<String, Object> parameters, ExchangeExportBatch batch) {
        ResolvedPermissions resolved = permissionResolver.resolve(batch.getSystemId(), batch.getTenantId(),
                longValue(parameters.get("tenantMemberId")));
        return new AuthenticatedContext(null, longValue(parameters.get("accountId")),
                longValue(parameters.get("platformId")), batch.getSystemId(), batch.getTenantId(),
                longValue(parameters.get("memberId")), longValue(parameters.get("tenantMemberId")),
                String.valueOf(parameters.getOrDefault("username", "job")),
                String.valueOf(parameters.getOrDefault("displayName", "后台导出")),
                String.valueOf(parameters.getOrDefault("mfaLevel", "PASSWORD")),
                resolved.roleIds(), resolved.permissions(), resolved.dataScopes());
    }

    private Map<String, Object> authorizationSnapshot(
            AuthenticatedContext context, String moduleCode, List<FieldDefinition> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleCode", moduleCode);
        result.put("roleIds", context.roleIds());
        result.put("permissions", context.permissions());
        result.put("dataScopes", context.dataScopes());
        result.put("maskedFields", fields.stream().filter(FieldDefinition::masked).map(FieldDefinition::code).toList());
        result.put("fieldMasks", fields.stream().filter(FieldDefinition::masked)
                .collect(java.util.stream.Collectors.toMap(FieldDefinition::code, FieldDefinition::maskStrategy)));
        return result;
    }

    private Map<String, Object> querySnapshot(ExportModels.ExportQuery input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lifecycleState", input.lifecycleState() == null ? "ACTIVE" : input.lifecycleState());
        result.put("tenantScope", input.tenantScope() == null ? "ALL" : input.tenantScope());
        result.put("search", input.search() == null ? "" : input.search());
        result.put("filters", input.filters() == null ? List.of() : input.filters());
        result.put("sortField", input.sortField() == null ? "updatedAt" : input.sortField());
        result.put("sortDirection", input.sortDirection() == null ? "DESC" : input.sortDirection());
        result.put("selectedRecordIds", input.selectedRecordIds() == null ? List.of() : input.selectedRecordIds());
        return result;
    }

    private ExportModels.ExportQuery queryFromSnapshot(Map<String, Object> source, List<String> fields) {
        return new ExportModels.ExportQuery(String.valueOf(source.getOrDefault("lifecycleState", "ACTIVE")),
                String.valueOf(source.getOrDefault("tenantScope", "ALL")),
                String.valueOf(source.getOrDefault("search", "")),
                objectMapper.convertValue(source.getOrDefault("filters", List.of()),
                        new TypeReference<List<Map<String, String>>>() { }),
                String.valueOf(source.getOrDefault("sortField", "updatedAt")),
                String.valueOf(source.getOrDefault("sortDirection", "DESC")),
                longList(source.get("selectedRecordIds")), fields);
    }

    private ExportModels.ExportBatchView view(String moduleCode, ExchangeExportBatch batch) {
        Map<String, Object> snapshot = new LinkedHashMap<>(parseObject(batch.getFilterSnapshotJson()));
        snapshot.remove("authorizedRecordIds");
        return new ExportModels.ExportBatchView(batch.getId(), moduleCode, batch.getJobId(), batch.getStatus(),
                value(batch.getTotalRows()), value(batch.getExportedRows()), batch.getResultFileId(),
                batch.getErrorMessage(), snapshot, parseStringList(batch.getSelectedFieldsJson()),
                batch.getCreatedAt(), batch.getStartedAt(), batch.getFinishedAt(), batch.getVersion());
    }

    private String stronger(String left, String right) {
        if (left == null || left.isBlank()) return right;
        if (right == null || right.isBlank()) return left;
        return maskPriority(left) >= maskPriority(right) ? left : right;
    }

    private int maskPriority(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "FULL", "MASK", "REDACT" -> 4;
            case "HASH" -> 3;
            case "PARTIAL" -> 2;
            case "LAST4" -> 1;
            default -> 4;
        };
    }

    private String mask(String value, String strategy) {
        return switch (strategy.strip().toUpperCase(Locale.ROOT)) {
            case "PARTIAL" -> value.length() <= 2 ? "*".repeat(value.length())
                    : value.substring(0, 1) + "***" + value.substring(value.length() - 1);
            case "LAST4" -> "***" + value.substring(Math.max(0, value.length() - 4));
            case "HASH" -> "[已散列]";
            default -> "***";
        };
    }

    private Object jsonValue(Object value) {
        if (value instanceof JsonNode node) {
            if (node.isTextual()) return node.asText();
            if (node.isNull()) return null;
            return node.toString();
        }
        return value;
    }

    private String text(Object value) {
        Object normalized = jsonValue(value);
        return normalized == null ? "" : String.valueOf(normalized);
    }

    private String csvCell(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize export snapshot", exception); }
    }

    private Map<String, Object> parseObject(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid export snapshot", exception); }
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<List<String>>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid export field snapshot", exception); }
    }

    private List<String> stringList(Object value) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, new TypeReference<List<String>>() { });
    }

    private List<Long> longList(Object value) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, new TypeReference<List<Long>>() { });
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String trace(BackgroundJobExecution execution) {
        return "export-job-" + execution.jobId();
    }

    private void notifyResult(
            AuthenticatedContext context, Long batchId, String moduleCode, boolean succeeded,
            long exportedRows, String errorMessage, String traceId) {
        try {
            messageService.notifyExportResult(context, batchId, moduleCode, succeeded, exportedRows, errorMessage, traceId);
        } catch (RuntimeException notificationFailure) {
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE_EXPORT_NOTIFICATION_FAILED", "EXPORT_BATCH", batchId.toString(),
                    "NOTIFICATION_FAILED", Map.of("message", Objects.toString(notificationFailure.getMessage(), "")));
        }
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof DomainException domain ? domain.code() : "EXPORT_EXECUTION_FAILED";
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(1900, value.length()));
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

    private record ExportPreparation(
            RuntimeModuleConfiguration configuration, List<FieldDefinition> fields,
            List<RuntimeRecordView> records, ExportModels.EstimateView estimate) {
    }

    private record FieldDefinition(
            String code, String name, String fieldType, boolean builtIn,
            Long referenceModuleId, String maskStrategy) {
        private FieldDefinition(String code, String name, String fieldType, boolean builtIn, Long referenceModuleId) {
            this(code, name, fieldType, builtIn, referenceModuleId, null);
        }

        private boolean masked() {
            return maskStrategy != null && !maskStrategy.isBlank();
        }

        private ExportModels.ExportFieldView view() {
            return new ExportModels.ExportFieldView(code, name, fieldType, builtIn, true, masked(), maskStrategy);
        }
    }
}
