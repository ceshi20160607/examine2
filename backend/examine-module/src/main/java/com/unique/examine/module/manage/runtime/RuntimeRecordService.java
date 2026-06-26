package com.unique.examine.module.manage.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.manage.runtime.WorkflowRuntimeMutationService;
import com.unique.examine.flow.manage.runtime.WorkflowRuntimeMutationService.WorkflowApprovalHook;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.entity.ModuleDynamicHistory;
import com.unique.examine.module.base.entity.ModuleDynamicRecord;
import com.unique.examine.module.base.entity.ModuleDynamicValue;
import com.unique.examine.module.base.entity.ModuleFieldDefinition;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleDynamicHistoryBaseService;
import com.unique.examine.module.base.service.ModuleDynamicRecordBaseService;
import com.unique.examine.module.base.service.ModuleDynamicValueBaseService;
import com.unique.examine.module.base.service.ModuleFieldDefinitionBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.config.ModuleConfigModels.DynamicListSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.FilterSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.SortSchema;
import com.unique.examine.module.manage.config.ModuleConfigService;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ActionExecutionRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ActionExecutionResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ActionView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.AppliedQueryMeta;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ApprovalSidebarHook;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.AsyncTaskRef;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.BusinessDetailView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.BusinessRecordRow;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ColumnPermissionVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.DataScopeView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.DetailTabView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.FieldChangeView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.FieldFilterCriterion;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.FieldMaskResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.FieldValueView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.FilterCapabilityVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.HistoryHook;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordHistoryEntry;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordMutationResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSaveRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSearchRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSortCriterion;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSummary;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ResultHook;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RowDetailTarget;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimeListSchemaVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimePageMeta;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimeRecordSearchVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.SelectionLimit;
import com.unique.examine.plat.manage.permission.PermissionModels.EffectivePermissionSnapshot;
import com.unique.examine.plat.manage.permission.PermissionService;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.SortCapabilityVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Runtime dynamic record service backed by persisted dynamic record/value/history tables.
 */
@Service
public class RuntimeRecordService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final int DELETED_YES = 1;
    private static final String DEFAULT_SCENE_CODE = "all";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final TypeReference<List<FieldChangeView>> FIELD_CHANGE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<FieldMaskResult>> FIELD_MASK_LIST = new TypeReference<>() {
    };

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleConfigService moduleConfigService;
    private final ModuleDefinitionBaseService moduleBaseService;
    private final ModuleFieldDefinitionBaseService fieldBaseService;
    private final ModuleDynamicRecordBaseService recordBaseService;
    private final ModuleDynamicValueBaseService valueBaseService;
    private final ModuleDynamicHistoryBaseService historyBaseService;
    private final WorkflowRuntimeMutationService workflowRuntimeMutationService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public RuntimeRecordService(ModuleSystemContextResolver contextResolver,
                                ModuleConfigService moduleConfigService,
                                ModuleDefinitionBaseService moduleBaseService,
                                ModuleFieldDefinitionBaseService fieldBaseService,
                                ModuleDynamicRecordBaseService recordBaseService,
                                ModuleDynamicValueBaseService valueBaseService,
                                ModuleDynamicHistoryBaseService historyBaseService,
                                WorkflowRuntimeMutationService workflowRuntimeMutationService,
                                PermissionService permissionService,
                                ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.moduleConfigService = moduleConfigService;
        this.moduleBaseService = moduleBaseService;
        this.fieldBaseService = fieldBaseService;
        this.recordBaseService = recordBaseService;
        this.valueBaseService = valueBaseService;
        this.historyBaseService = historyBaseService;
        this.workflowRuntimeMutationService = workflowRuntimeMutationService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Return the runtime list schema hook used by dynamic record pages.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param sceneCode optional scene code
     * @return list schema view
     */
    public RuntimeListSchemaVO listSchema(String systemId, String moduleId, String sceneCode) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        DynamicListSchema schema = moduleConfigService.listSchema(systemId, moduleId, sceneCode);
        return new RuntimeListSchemaVO(schema.moduleId(), schema.moduleCode(), safeText(schema.sceneCode(),
                DEFAULT_SCENE_CODE), schema.columns().stream()
                .filter(column -> !"HIDDEN".equalsIgnoreCase(fieldPermissionMode(permission, schema.moduleId(),
                        column.fieldId(), column.fieldCode())))
                .map(column -> new ColumnPermissionVO(column.fieldId(), column.fieldCode(), column.label(),
                        column.width(), column.visibleDefault(), column.configurable(), true,
                        fieldPermissionMode(permission, schema.moduleId(), column.fieldId(), column.fieldCode()),
                        fieldMaskRule(permission, schema.moduleId(), column.fieldId(), column.fieldCode(),
                                column.maskRule()), null))
                .toList(), schema.filters().stream()
                .map(this::toFilterCapability)
                .toList(), schema.sorters().stream()
                .map(this::toSortCapability)
                .toList(), new RuntimePageMeta(schema.page().defaultPageSize(), schema.page().allowedPageSizes(),
                schema.page().serverSide(), schema.page().cursorSupported()),
                new RowDetailTarget(schema.rowClickTarget().targetType(), schema.rowClickTarget().route(),
                        schema.rowClickTarget().drawerCode(), schema.rowClickTarget().preserveListContext(),
                        "点击数据行打开详情；复选框、按钮、链接、输入框不会误触详情。"),
                schema.toolbarActions().stream().map(this::toActionView)
                        .map(action -> withPermission(action, permission)).toList(),
                moduleConfigService.actions(systemId, moduleId).stream().filter(action -> "ROW".equals(action.position()))
                        .map(this::toActionView).map(action -> withPermission(action, permission)).toList(),
                schema.batchActions().stream().map(this::toActionView)
                        .map(action -> withPermission(action, permission)).toList(),
                dataScope(context), context.permissionSnapshotId(), context.permissionVersion(),
                "runtime_schema_" + schema.moduleId());
    }

    /**
     * Search runtime records with server-side paging, filtering, sorting, permissions, and data scope metadata.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request search request
     * @return search response
     */
    public RuntimeRecordSearchVO search(String systemId, String moduleId, RecordSearchRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        RuntimeListSchemaVO schema = listSchema(systemId, moduleId, requestSceneCode(request));
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        List<FieldFilterCriterion> acceptedFilters = acceptedFilters(request, fields);
        List<FieldFilterCriterion> rejectedFilters = rejectedFilters(request, fields);
        List<RecordSortCriterion> acceptedSorts = acceptedSorts(request, fields);
        List<RecordSortCriterion> rejectedSorts = rejectedSorts(request, fields);

        List<BusinessRecordRow> matchedRows = recordBaseService.list(new LambdaQueryWrapper<ModuleDynamicRecord>()
                        .eq(ModuleDynamicRecord::getSystemId, context.systemId())
                        .eq(ModuleDynamicRecord::getTenantId, context.tenantId())
                        .eq(ModuleDynamicRecord::getModuleId, module.getId())
                        .eq(ModuleDynamicRecord::getDeleted, DELETED_NO)
                        .orderByDesc(ModuleDynamicRecord::getUpdatedAt))
                .stream()
                .filter(record -> dataScopeAllows(permission, context, record))
                .map(record -> toRecordRow(context, permission, module, record, fields))
                .filter(row -> matchesKeyword(row, requestKeyword(request)))
                .filter(row -> matchesFilters(row, acceptedFilters))
                .toList();
        List<BusinessRecordRow> sortedRows = sortRows(matchedRows, acceptedSorts);
        int pageNo = pageNo(request);
        int pageSize = pageSize(request);
        int start = Math.min((pageNo - 1) * pageSize, sortedRows.size());
        int end = Math.min(start + pageSize, sortedRows.size());
        List<BusinessRecordRow> records = sortedRows.subList(start, end);
        PageResult<BusinessRecordRow> page = new PageResult<>(records, pageNo, pageSize, sortedRows.size(),
                end < sortedRows.size());
        AppliedQueryMeta appliedQuery = new AppliedQueryMeta(requestKeyword(request), requestSceneCode(request),
                acceptedFilters, rejectedFilters, acceptedSorts, rejectedSorts);
        return new RuntimeRecordSearchVO(schema, page, appliedQuery, dataScope(context),
                List.of("PERSISTED_RECORDS", "FIELD_PERMISSION_APPLIED", "DATA_SCOPE_ENFORCED",
                        "SERVER_SIDE_PAGE"));
    }

    /**
     * Return one record detail with business tabs and approval sidebar hook.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @return detail view
     */
    public BusinessDetailView detail(String systemId, String moduleId, String recordId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleDynamicRecord record = requireRecord(context, module, recordId);
        requireDataScope(permission, context, record);
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        BusinessRecordRow row = toRecordRow(context, permission, module, record, fields);
        return new BusinessDetailView(new RecordSummary(String.valueOf(record.getId()), row.title(), row.summary(),
                safeText(record.getStatus(), STATUS_DRAFT), statusColor(record.getStatus()),
                Objects.isNull(record.getOwnerMemberId()) ? null : String.valueOf(record.getOwnerMemberId()),
                record.getCreatedAt(), record.getUpdatedAt()), row.fields(), detailTabs(record, row),
                approvalSidebar(context, module, record), fieldMaskResults(row.fields()), row.actions(), row.rowDetailTarget(),
                dataScope(context), new HistoryHook("/api/v1/systems/" + systemId + "/runtime/modules/"
                + moduleId + "/records/" + recordId + "/history", true, "module.history.read"),
                context.permissionVersion());
    }

    /**
     * Create a dynamic record.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request save request
     * @param idempotencyKey optional idempotency key
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResult create(String systemId, String moduleId, RecordSaveRequest request,
                                       String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        requireActionPermission(permission, "record.create");
        ModuleDefinition module = requireModule(context, moduleId);
        Map<String, Object> values = requestValues(request);
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        requireWritableFields(permission, module, fields, values);
        LocalDateTime now = LocalDateTime.now();
        ModuleDynamicRecord record = new ModuleDynamicRecord();
        record.setSystemId(context.systemId());
        record.setTenantId(context.tenantId());
        record.setModuleId(module.getId());
        record.setRecordNo("R" + System.currentTimeMillis());
        record.setTitle(resolveTitle(values));
        record.setStatus(safeText(valueAsString(values.get("status")), STATUS_DRAFT));
        record.setOwnerMemberId(context.systemMemberId());
        record.setPermissionSnapshotId(context.permissionSnapshotId());
        record.setCreatedBy(context.systemMemberId());
        record.setUpdatedBy(context.systemMemberId());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(DELETED_NO);
        recordBaseService.saveEntity(record);
        saveValues(record, fields, values);
        List<FieldChangeView> changes = fieldChanges(null, values);
        saveHistory(context, module, record, "record.create", changes, "WEB");
        return mutationResult(context, module, record, "CREATED", changes, idempotencyKey);
    }

    /**
     * Update a dynamic record.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param request save request
     * @param idempotencyKey optional idempotency key
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResult update(String systemId, String moduleId, String recordId, RecordSaveRequest request,
                                       String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        requireActionPermission(permission, "record.edit");
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleDynamicRecord record = requireRecord(context, module, recordId);
        requireDataScope(permission, context, record);
        Map<String, Object> before = valueMap(record.getId());
        Map<String, Object> values = requestValues(request);
        requireWritableFields(permission, module, fieldsForModule(module.getId()), values);
        if (values.containsKey("title")) {
            record.setTitle(resolveTitle(values));
        }
        if (values.containsKey("status")) {
            record.setStatus(valueAsString(values.get("status")));
        }
        record.setUpdatedBy(context.systemMemberId());
        record.setUpdatedAt(LocalDateTime.now());
        recordBaseService.updateById(record);
        saveValues(record, fieldsForModule(module.getId()), values);
        List<FieldChangeView> changes = fieldChanges(before, values);
        saveHistory(context, module, record, "record.update", changes, "WEB");
        return mutationResult(context, module, record, "UPDATED", changes, idempotencyKey);
    }

    /**
     * Delete a dynamic record with logical delete semantics.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param idempotencyKey optional idempotency key
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResult delete(String systemId, String moduleId, String recordId, String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        requireActionPermission(permission, "record.delete");
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleDynamicRecord record = requireRecord(context, module, recordId);
        requireDataScope(permission, context, record);
        record.setDeleted(DELETED_YES);
        record.setUpdatedBy(context.systemMemberId());
        record.setUpdatedAt(LocalDateTime.now());
        recordBaseService.updateById(record);
        List<FieldChangeView> changes = List.of(new FieldChangeView("deleted", false, true,
                "READABLE", "逻辑删除保留历史和审计。"));
        saveHistory(context, module, record, "record.delete", changes, "WEB");
        return mutationResult(context, module, record, "DELETED", changes, idempotencyKey);
    }

    /**
     * Execute one row action with disabled reason and result hook metadata.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param actionCode action code
     * @param request action request
     * @param idempotencyKey optional idempotency key
     * @return action result
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionExecutionResult executeAction(String systemId, String moduleId, String recordId, String actionCode,
                                               ActionExecutionRequest request, String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        requireActionPermission(permission, actionCode);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleDynamicRecord record = requireRecord(context, module, recordId);
        requireDataScope(permission, context, record);
        String disabledReason = actionDisabledReason(record, actionCode, request);
        boolean accepted = Objects.isNull(disabledReason);
        if (accepted) {
            applyAction(context, module, record, actionCode, request, idempotencyKey);
        }
        ResultHook resultHook = actionResultHook(actionCode);
        AsyncTaskRef asyncTask = resultHook.returnsAsyncTask() && accepted
                ? new AsyncTaskRef("task_" + RequestContext.current().traceId(), "RUNTIME_RECORD_ACTION",
                "QUEUED", 0, true, true, null, null) : null;
        RequestContext requestContext = RequestContext.current();
        return new ActionExecutionResult(recordId, actionCode, accepted ? "ACTION_ACCEPTED" : "ACTION_BLOCKED",
                accepted, disabledReason, resultHook, asyncTask, context.permissionVersion(),
                safeText(idempotencyKey, "idem_" + requestContext.traceId()), requestContext.traceId(),
                auditLogId(requestContext), LocalDateTime.now());
    }

    /**
     * Return record history with field diffs, source, permission snapshot, and desensitize results.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param pageNo page number
     * @param pageSize page size
     * @return history page
     */
    public PageResult<RecordHistoryEntry> history(String systemId, String moduleId, String recordId,
                                                  int pageNo, int pageSize) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        EffectivePermissionSnapshot permission = permissionService.effective(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleDynamicRecord record = requireRecord(context, module, recordId);
        requireDataScope(permission, context, record);
        int resolvedPageNo = pageNo <= 0 ? 1 : pageNo;
        int resolvedPageSize = pageSize <= 0 ? 20 : pageSize;
        int offset = (resolvedPageNo - 1) * resolvedPageSize;
        LambdaQueryWrapper<ModuleDynamicHistory> wrapper = new LambdaQueryWrapper<ModuleDynamicHistory>()
                .eq(ModuleDynamicHistory::getRecordId, record.getId());
        long total = historyBaseService.count(wrapper);
        List<RecordHistoryEntry> records = historyBaseService.list(new LambdaQueryWrapper<ModuleDynamicHistory>()
                        .eq(ModuleDynamicHistory::getRecordId, record.getId())
                        .orderByDesc(ModuleDynamicHistory::getOperatedAt)
                        .last("LIMIT " + offset + "," + resolvedPageSize))
                .stream()
                .map(this::toHistoryEntry)
                .toList();
        return new PageResult<>(records, resolvedPageNo, resolvedPageSize, total, offset + records.size() < total);
    }

    private ModuleDefinition requireModule(ModuleSystemContext context, String moduleId) {
        Long id = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        ModuleDefinition module = moduleBaseService.getOne(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getId, id)
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(module)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模块不存在");
        }
        return module;
    }

    private ModuleDynamicRecord requireRecord(ModuleSystemContext context, ModuleDefinition module, String recordId) {
        Long id = contextResolver.parseRequiredId(recordId, "记录ID格式不正确");
        ModuleDynamicRecord record = recordBaseService.getOne(new LambdaQueryWrapper<ModuleDynamicRecord>()
                .eq(ModuleDynamicRecord::getId, id)
                .eq(ModuleDynamicRecord::getSystemId, context.systemId())
                .eq(ModuleDynamicRecord::getTenantId, context.tenantId())
                .eq(ModuleDynamicRecord::getModuleId, module.getId())
                .eq(ModuleDynamicRecord::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(record)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "记录不存在");
        }
        return record;
    }

    private List<ModuleFieldDefinition> fieldsForModule(Long moduleId) {
        return fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                .eq(ModuleFieldDefinition::getStatus, ENABLED)
                .orderByAsc(ModuleFieldDefinition::getSortOrder)
                .orderByAsc(ModuleFieldDefinition::getId));
    }

    private BusinessRecordRow toRecordRow(ModuleSystemContext context, EffectivePermissionSnapshot permission,
                                          ModuleDefinition module,
                                          ModuleDynamicRecord record, List<ModuleFieldDefinition> fields) {
        List<FieldValueView> fieldValues = fieldValues(permission, module, record, fields);
        Map<String, String> disabledReasons = disabledReasons(record);
        return new BusinessRecordRow(String.valueOf(record.getId()), record.getTitle(),
                safeText(record.getRecordNo(), module.getModuleName()), fieldValues, rowActions(record, permission),
                disabledReasons, rowDetailTarget(module), dataScope(context), context.permissionVersion(),
                record.getUpdatedAt());
    }

    private List<FieldValueView> fieldValues(EffectivePermissionSnapshot permission, ModuleDefinition module,
                                             ModuleDynamicRecord record, List<ModuleFieldDefinition> fields) {
        Map<Long, ModuleDynamicValue> values = valuesByField(record.getId());
        return fields.stream()
                .filter(field -> !"HIDDEN".equalsIgnoreCase(fieldPermissionMode(permission, module.getId(),
                        field.getId(), field.getFieldCode())))
                .map(field -> {
                    Object value = valueOf(values.get(field.getId()), field);
                    String permissionMode = fieldPermissionMode(permission, module.getId(), field.getId(),
                            field.getFieldCode());
                    String maskRule = fieldMaskRule(permission, module.getId(), field.getId(), field.getFieldCode(),
                            field.getMaskRule());
                    Object visibleValue = "MASKED".equalsIgnoreCase(permissionMode) ? null : value;
                    String display = Objects.isNull(value) ? "" : String.valueOf(value);
                    if ("MASKED".equalsIgnoreCase(permissionMode)) {
                        display = "******";
                    }
                    return new FieldValueView(field.getFieldCode(), field.getFieldName(), visibleValue, display,
                            permissionMode, maskRule, true, null);
                })
                .toList();
    }

    private Map<Long, ModuleDynamicValue> valuesByField(Long recordId) {
        Map<Long, ModuleDynamicValue> values = new LinkedHashMap<>();
        for (ModuleDynamicValue value : valueBaseService.list(new LambdaQueryWrapper<ModuleDynamicValue>()
                .eq(ModuleDynamicValue::getRecordId, recordId))) {
            values.put(value.getFieldId(), value);
        }
        return values;
    }

    private Map<String, Object> valueMap(Long recordId) {
        Map<Long, ModuleDynamicValue> byField = valuesByField(recordId);
        Map<String, Object> values = new LinkedHashMap<>();
        for (ModuleDynamicValue value : byField.values()) {
            ModuleFieldDefinition field = fieldBaseService.getById(value.getFieldId());
            if (Objects.nonNull(field)) {
                values.put(field.getFieldCode(), valueOf(value, field));
            }
        }
        return values;
    }

    private Object valueOf(ModuleDynamicValue value, ModuleFieldDefinition field) {
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

    private void saveValues(ModuleDynamicRecord record, List<ModuleFieldDefinition> fields,
                            Map<String, Object> values) {
        for (ModuleFieldDefinition field : fields) {
            if (!values.containsKey(field.getFieldCode())) {
                continue;
            }
            valueBaseService.remove(new LambdaQueryWrapper<ModuleDynamicValue>()
                    .eq(ModuleDynamicValue::getRecordId, record.getId())
                    .eq(ModuleDynamicValue::getFieldId, field.getId()));
            ModuleDynamicValue value = new ModuleDynamicValue();
            value.setRecordId(record.getId());
            value.setModuleId(record.getModuleId());
            value.setFieldId(field.getId());
            setTypedValue(value, field, values.get(field.getFieldCode()));
            value.setCreatedAt(LocalDateTime.now());
            value.setUpdatedAt(LocalDateTime.now());
            valueBaseService.saveEntity(value);
        }
    }

    private void setTypedValue(ModuleDynamicValue value, ModuleFieldDefinition field, Object rawValue) {
        if (Objects.isNull(rawValue)) {
            return;
        }
        switch (field.getStorageType()) {
            case "DECIMAL" -> value.setValueNumber(new BigDecimal(String.valueOf(rawValue)));
            case "DATETIME" -> value.setValueDatetime(LocalDateTime.parse(String.valueOf(rawValue)));
            case "JSON" -> value.setValueJson(toJson(rawValue));
            default -> value.setValueText(String.valueOf(rawValue));
        }
    }

    private void saveHistory(ModuleSystemContext context, ModuleDefinition module, ModuleDynamicRecord record,
                             String actionCode, List<FieldChangeView> fieldDiff, String sourceType) {
        RequestContext requestContext = RequestContext.current();
        ModuleDynamicHistory history = new ModuleDynamicHistory();
        history.setSystemId(context.systemId());
        history.setTenantId(context.tenantId());
        history.setModuleId(module.getId());
        history.setRecordId(record.getId());
        history.setActionCode(actionCode);
        history.setFieldDiff(toJson(fieldDiff));
        history.setSourceType(sourceType);
        history.setPermissionSnapshotId(context.permissionSnapshotId());
        history.setDesensitizeResult(toJson(List.of()));
        history.setTraceId(requestContext.traceId());
        history.setAuditLogId(auditLogId(requestContext));
        history.setOperatedBy(context.systemMemberId());
        history.setOperatedAt(LocalDateTime.now());
        historyBaseService.saveEntity(history);
    }

    private RecordMutationResult mutationResult(ModuleSystemContext context, ModuleDefinition module,
                                                ModuleDynamicRecord record, String result,
                                                List<FieldChangeView> changes, String idempotencyKey) {
        RequestContext requestContext = RequestContext.current();
        return new RecordMutationResult(String.valueOf(record.getId()), result, changes, List.of(),
                rowDetailTarget(module), approvalSidebar(context, module, record), context.permissionSnapshotId(),
                context.permissionVersion(), safeText(idempotencyKey, "idem_" + requestContext.traceId()),
                requestContext.traceId(), auditLogId(requestContext), LocalDateTime.now());
    }

    private RecordHistoryEntry toHistoryEntry(ModuleDynamicHistory history) {
        return new RecordHistoryEntry(String.valueOf(history.getId()), String.valueOf(history.getRecordId()),
                history.getActionCode(), readFieldChanges(history.getFieldDiff()), history.getSourceType(),
                history.getPermissionSnapshotId(), "perm_live", readFieldMasks(history.getDesensitizeResult()),
                history.getTraceId(), history.getAuditLogId(), String.valueOf(history.getOperatedBy()),
                history.getOperatedAt());
    }

    private List<DetailTabView> detailTabs(ModuleDynamicRecord record, BusinessRecordRow row) {
        Map<String, Object> basePayload = new LinkedHashMap<>();
        basePayload.put("fields", row.fields());
        basePayload.put("recordNo", record.getRecordNo());
        basePayload.put("status", record.getStatus());
        Map<String, Object> logPayload = new LinkedHashMap<>();
        logPayload.put("historyEndpoint", "/records/" + record.getId() + "/history");
        logPayload.put("serverSidePage", true);
        logPayload.put("latestTraceId", RequestContext.current().traceId());
        return List.of(new DetailTabView("base", "基础资料", true, "module.read", null, basePayload),
                new DetailTabView("operationLogs", "操作记录", true, "module.history.read", null, logPayload));
    }

    private ApprovalSidebarHook approvalSidebar(ModuleSystemContext context, ModuleDefinition module,
                                                ModuleDynamicRecord record) {
        WorkflowApprovalHook hook = workflowRuntimeMutationService.approvalHook(context.systemId(),
                context.tenantId(), module.getId(), record.getId());
        if (!hook.visible()) {
            return new ApprovalSidebarHook(false, null, null, null, "NONE", null, null);
        }
        return new ApprovalSidebarHook(true, hook.flowInstanceId(), hook.currentNodeName(), hook.pendingTaskId(),
                hook.status(), hook.actionTarget(), null);
    }

    private List<FieldMaskResult> fieldMaskResults(List<FieldValueView> fields) {
        return fields.stream()
                .filter(field -> !"NONE".equals(field.maskRule()))
                .map(field -> new FieldMaskResult(field.fieldCode(), field.permissionMode(), field.maskRule(),
                        true, field.displayValue(), null))
                .toList();
    }

    private List<ActionView> rowActions(ModuleDynamicRecord record, EffectivePermissionSnapshot permission) {
        boolean archived = STATUS_ARCHIVED.equals(record.getStatus());
        boolean pendingApproval = "PENDING_APPROVAL".equals(record.getStatus());
        boolean draft = STATUS_DRAFT.equals(record.getStatus());
        return List.of(action("record.edit", "编辑", "UPDATE", "ROW",
                        !archived && hasActionPermission(permission, "record.edit"),
                        firstReason(archived ? "已归档记录不可编辑" : null,
                                actionDisabledReason(permission, "record.edit")), syncHook("recordEditDrawer")),
                action("record.submitApproval", "提交审批", "WORKFLOW", "ROW",
                        !archived && !pendingApproval && hasActionPermission(permission, "record.submitApproval"),
                        firstReason(submitApprovalDisabledReason(archived, pendingApproval),
                                actionDisabledReason(permission, "record.submitApproval")),
                        syncHook("approvalSubmitResultDrawer")),
                action("record.delete", "删除", "DELETE", "ROW",
                        draft && hasActionPermission(permission, "record.delete"),
                        firstReason(draft ? null : "仅草稿记录可删除",
                                actionDisabledReason(permission, "record.delete")), syncHook("deleteConfirmDrawer")));
    }

    private List<ActionView> rowActions(String status) {
        ModuleDynamicRecord record = new ModuleDynamicRecord();
        record.setStatus(status);
        return rowActions(record, superAdminPermissionSnapshot());
    }

    private ActionView action(String actionCode, String actionName, String actionType, String position,
                              boolean enabled, String disabledReason, ResultHook resultHook) {
        return new ActionView(actionCode, actionName, actionType, position, enabled, disabledReason,
                new SelectionLimit("SINGLE", 1, 1, List.of(), true, null), resultHook, true);
    }

    private ActionView toActionView(com.unique.examine.module.manage.config.ModuleConfigModels.ActionConfigVO action) {
        return new ActionView(action.actionCode(), action.actionName(), action.actionType(), action.position(),
                action.enabled(), action.disabledReason(),
                new SelectionLimit(action.selectionRule().selectionMode(), action.selectionRule().minSelected(),
                        action.selectionRule().maxSelected(), action.selectionRule().requiredStatuses(),
                        action.selectionRule().sameTenantRequired(), action.selectionRule().forbiddenReason()),
                new ResultHook(action.resultContract().resultType(), action.resultContract().resultDrawer(),
                        action.resultContract().returnsAuditLog(), action.resultContract().returnsAsyncTask(),
                        action.resultContract().traceField(), action.resultContract().userVisibleStates()),
                action.idempotencyRequired());
    }

    private ActionView withPermission(ActionView action, EffectivePermissionSnapshot permission) {
        String permissionReason = actionDisabledReason(permission, action.actionCode());
        boolean enabled = action.enabled() && Objects.isNull(permissionReason);
        return new ActionView(action.actionCode(), action.actionName(), action.actionType(), action.position(),
                enabled, firstReason(action.disabledReason(), permissionReason), action.selectionLimit(),
                action.resultHook(), action.idempotencyRequired());
    }

    private FilterCapabilityVO toFilterCapability(FilterSchema filter) {
        return new FilterCapabilityVO(filter.fieldId(), filter.fieldCode(), filter.label(), filter.fieldType(),
                filter.operators(), filter.quickFilter(), filter.advanced(), true, filter.permissionMode(), null);
    }

    private SortCapabilityVO toSortCapability(SortSchema sort) {
        return new SortCapabilityVO(sort.fieldId(), sort.fieldCode(), sort.label(), List.of("ASC", "DESC"),
                sort.defaultDirection(), sort.defaultSort(), sort.supported(), null);
    }

    private DataScopeView dataScope(ModuleSystemContext context) {
        return new DataScopeView(String.valueOf(context.systemId()), String.valueOf(context.tenantId()),
                String.valueOf(context.systemMemberId()), "SYSTEM_TENANT",
                "systemId == currentSystem && tenantId == currentTenant", true, context.permissionVersion());
    }

    private void requireActionPermission(EffectivePermissionSnapshot permission, String actionCode) {
        if (!hasActionPermission(permission, actionCode)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, actionDisabledReason(permission, actionCode));
        }
    }

    private boolean hasActionPermission(EffectivePermissionSnapshot permission, String actionCode) {
        Map<String, Boolean> actions = permission.action();
        return Boolean.TRUE.equals(actions.get("*")) || Boolean.TRUE.equals(actions.get(actionCode));
    }

    private String actionDisabledReason(EffectivePermissionSnapshot permission, String actionCode) {
        if (hasActionPermission(permission, actionCode)) {
            return null;
        }
        return "当前角色未配置动作权限：" + actionCode;
    }

    private void requireWritableFields(EffectivePermissionSnapshot permission, ModuleDefinition module,
                                       List<ModuleFieldDefinition> fields, Map<String, Object> values) {
        for (ModuleFieldDefinition field : fields) {
            if (values.containsKey(field.getFieldCode()) && !isWritableField(permission, module, field)) {
                throw new BusinessException(CommonErrorCode.PERMISSION_DENIED,
                        "当前角色没有字段写入权限：" + field.getFieldName());
            }
        }
    }

    private boolean isWritableField(EffectivePermissionSnapshot permission, ModuleDefinition module,
                                    ModuleFieldDefinition field) {
        return "WRITABLE".equalsIgnoreCase(fieldPermissionMode(permission, module.getId(), field.getId(),
                field.getFieldCode()));
    }

    private String fieldPermissionMode(EffectivePermissionSnapshot permission, Object moduleId,
                                       Object fieldId, String fieldCode) {
        Map<String, String> fields = permission.field();
        return safeText(firstFieldPermission(fields, moduleId, fieldId, fieldCode), "READABLE")
                .toUpperCase(Locale.ROOT);
    }

    private String firstFieldPermission(Map<String, String> fields, Object moduleId, Object fieldId,
                                        String fieldCode) {
        List<String> keys = List.of("*", String.valueOf(moduleId) + "." + fieldId,
                String.valueOf(moduleId) + "." + fieldCode, String.valueOf(fieldId), fieldCode);
        for (String key : keys) {
            if (StringUtils.hasText(fields.get(key))) {
                return fields.get(key);
            }
        }
        return null;
    }

    private String fieldMaskRule(EffectivePermissionSnapshot permission, Object moduleId, Object fieldId,
                                 String fieldCode, String configuredMaskRule) {
        String mode = fieldPermissionMode(permission, moduleId, fieldId, fieldCode);
        if ("MASKED".equalsIgnoreCase(mode)) {
            return safeText(readJsonString(configuredMaskRule, "MASKED"), "MASKED");
        }
        return readJsonString(configuredMaskRule, "NONE");
    }

    private void requireDataScope(EffectivePermissionSnapshot permission, ModuleSystemContext context,
                                  ModuleDynamicRecord record) {
        if (!dataScopeAllows(permission, context, record)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "当前数据不在角色数据范围内");
        }
    }

    private boolean dataScopeAllows(EffectivePermissionSnapshot permission, ModuleSystemContext context,
                                    ModuleDynamicRecord record) {
        String type = String.valueOf(permission.dataScope().getOrDefault("type", "ALL"))
                .toUpperCase(Locale.ROOT);
        if ("ALL".equals(type)) {
            return true;
        }
        if ("SELF".equals(type)) {
            return Objects.equals(record.getOwnerMemberId(), context.systemMemberId())
                    || Objects.equals(record.getCreatedBy(), context.systemMemberId());
        }
        // 部门和自定义范围需要组织树表达式编译；在未完成编译前按本人范围收紧，避免越权。
        return Objects.equals(record.getOwnerMemberId(), context.systemMemberId())
                || Objects.equals(record.getCreatedBy(), context.systemMemberId());
    }

    private String firstReason(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private EffectivePermissionSnapshot superAdminPermissionSnapshot() {
        return new EffectivePermissionSnapshot("eps_runtime_internal", "perm_runtime_internal", "0", "0",
                List.of("SYSTEM_SUPER_ADMIN"), List.of(), Map.of("*", "WRITABLE"), Map.of("*", true),
                Map.of("type", "ALL"), null, List.of());
    }

    private RowDetailTarget rowDetailTarget(ModuleDefinition module) {
        return new RowDetailTarget("DRAWER",
                "/systems/" + module.getSystemId() + "/runtime/modules/" + module.getId() + "/records/{recordId}",
                "moduleRecordDetailDrawer", true,
                "点击数据行打开详情；复选框、按钮、链接、输入框不会误触详情。");
    }

    private Map<String, String> disabledReasons(ModuleDynamicRecord record) {
        Map<String, String> disabledReasons = new LinkedHashMap<>();
        if (STATUS_ARCHIVED.equals(record.getStatus())) {
            disabledReasons.put("record.edit", "已归档记录不可编辑");
            disabledReasons.put("record.submitApproval", "已归档记录不可提交审批");
        }
        if (!STATUS_DRAFT.equals(record.getStatus())) {
            disabledReasons.put("record.delete", "仅草稿记录可删除");
        }
        return disabledReasons;
    }

    private List<FieldFilterCriterion> acceptedFilters(RecordSearchRequest request, List<ModuleFieldDefinition> fields) {
        return safeFilters(request).stream()
                .filter(filter -> filterAccepted(filter, fields))
                .toList();
    }

    private List<FieldFilterCriterion> rejectedFilters(RecordSearchRequest request, List<ModuleFieldDefinition> fields) {
        return safeFilters(request).stream()
                .filter(filter -> !filterAccepted(filter, fields))
                .toList();
    }

    private boolean filterAccepted(FieldFilterCriterion filter, List<ModuleFieldDefinition> fields) {
        return fields.stream().anyMatch(field -> field.getFieldCode().equals(filter.fieldCode())
                && readJsonList(field.getFilterOperators()).contains(filter.operator()));
    }

    private List<RecordSortCriterion> acceptedSorts(RecordSearchRequest request, List<ModuleFieldDefinition> fields) {
        return safeSorts(request).stream()
                .filter(sort -> sortAccepted(sort, fields))
                .toList();
    }

    private List<RecordSortCriterion> rejectedSorts(RecordSearchRequest request, List<ModuleFieldDefinition> fields) {
        return safeSorts(request).stream()
                .filter(sort -> !sortAccepted(sort, fields))
                .toList();
    }

    private boolean sortAccepted(RecordSortCriterion sort, List<ModuleFieldDefinition> fields) {
        if ("updatedAt".equals(sort.fieldCode())) {
            return true;
        }
        return fields.stream().anyMatch(field -> field.getFieldCode().equals(sort.fieldCode())
                && Objects.equals(field.getSortable(), ENABLED));
    }

    private boolean matchesKeyword(BusinessRecordRow row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowered = keyword.toLowerCase(Locale.ROOT);
        return row.title().toLowerCase(Locale.ROOT).contains(lowered)
                || row.summary().toLowerCase(Locale.ROOT).contains(lowered)
                || row.fields().stream().anyMatch(field -> field.displayValue().toLowerCase(Locale.ROOT).contains(lowered));
    }

    private boolean matchesFilters(BusinessRecordRow row, List<FieldFilterCriterion> filters) {
        for (FieldFilterCriterion filter : filters) {
            Object fieldValue = fieldValue(row, filter.fieldCode());
            if (!matchesFilter(fieldValue, filter.operator(), filter.value())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(Object fieldValue, String operator, Object filterValue) {
        if ("IS_NULL".equals(operator)) {
            return Objects.isNull(fieldValue) || !StringUtils.hasText(String.valueOf(fieldValue));
        }
        if ("IS_NOT_NULL".equals(operator)) {
            return Objects.nonNull(fieldValue) && StringUtils.hasText(String.valueOf(fieldValue));
        }
        if (Objects.isNull(fieldValue)) {
            return false;
        }
        String current = String.valueOf(fieldValue);
        return switch (safeText(operator, "EQ")) {
            case "LIKE" -> current.toLowerCase(Locale.ROOT).contains(String.valueOf(filterValue).toLowerCase(Locale.ROOT));
            case "IN" -> valueList(filterValue).contains(current);
            default -> current.equals(String.valueOf(filterValue));
        };
    }

    private Object fieldValue(BusinessRecordRow row, String fieldCode) {
        return row.fields().stream()
                .filter(field -> field.fieldCode().equals(fieldCode))
                .map(FieldValueView::value)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<BusinessRecordRow> sortRows(List<BusinessRecordRow> rows, List<RecordSortCriterion> sorts) {
        List<BusinessRecordRow> sorted = new ArrayList<>(rows);
        RecordSortCriterion sort = sorts.isEmpty() ? new RecordSortCriterion("updatedAt", "DESC") : sorts.get(0);
        Comparator<BusinessRecordRow> comparator = "updatedAt".equals(sort.fieldCode())
                ? Comparator.comparing(BusinessRecordRow::updatedAt)
                : Comparator.comparing(row -> String.valueOf(fieldValue(row, sort.fieldCode())));
        if ("DESC".equalsIgnoreCase(sort.direction())) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);
        return sorted;
    }

    private void applyAction(ModuleSystemContext context, ModuleDefinition module, ModuleDynamicRecord record,
                             String actionCode, ActionExecutionRequest request, String idempotencyKey) {
        if ("record.submitApproval".equals(actionCode)) {
            record.setStatus("PENDING_APPROVAL");
            record.setUpdatedBy(context.systemMemberId());
            record.setUpdatedAt(LocalDateTime.now());
            recordBaseService.updateById(record);
            workflowRuntimeMutationService.startForRecord(context.systemId(), context.tenantId(), module.getId(),
                    record.getId(), context.systemMemberId(),
                    safeText(idempotencyKey, "idem_" + RequestContext.current().traceId()));
        }
        if ("record.batchArchive".equals(actionCode) || "record.archive".equals(actionCode)) {
            record.setStatus(STATUS_ARCHIVED);
            record.setUpdatedBy(context.systemMemberId());
            record.setUpdatedAt(LocalDateTime.now());
            recordBaseService.updateById(record);
        }
        saveHistory(context, module, record, actionCode,
                List.of(new FieldChangeView("action", null, actionCode, "READABLE",
                        safeText(request == null ? null : request.reason(), "动作已执行"))), "WEB_ACTION");
    }

    private String actionDisabledReason(ModuleDynamicRecord record, String actionCode, ActionExecutionRequest request) {
        if ("record.delete".equals(actionCode) && !STATUS_DRAFT.equals(record.getStatus())) {
            return "仅草稿记录可删除";
        }
        if ("record.submitApproval".equals(actionCode) && STATUS_ARCHIVED.equals(record.getStatus())) {
            return "已归档记录不可提交审批";
        }
        if ("record.submitApproval".equals(actionCode) && "PENDING_APPROVAL".equals(record.getStatus())) {
            return "记录已在审批中";
        }
        if ("record.batchArchive".equals(actionCode) && selectedCount(request) > 200) {
            return "批量归档最多支持 200 条";
        }
        return null;
    }

    private String submitApprovalDisabledReason(boolean archived, boolean pendingApproval) {
        if (archived) {
            return "已归档记录不可提交审批";
        }
        if (pendingApproval) {
            return "记录已在审批中";
        }
        return null;
    }

    private ResultHook actionResultHook(String actionCode) {
        if (actionCode.startsWith("record.batch") || "record.export".equals(actionCode)) {
            return new ResultHook("ASYNC_TASK", "asyncTaskDrawer", true, true, "taskId",
                    List.of("QUEUED", "RUNNING", "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELED"));
        }
        if ("record.submitApproval".equals(actionCode)) {
            return syncHook("approvalSubmitResultDrawer");
        }
        return syncHook("runtimeActionResultDrawer");
    }

    private ResultHook syncHook(String drawerCode) {
        return new ResultHook("SYNC_RESULT", drawerCode, true, false, "traceId",
                List.of("SUCCESS", "FAILED", "BLOCKED"));
    }

    private int selectedCount(ActionExecutionRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.selectedRecordIds())
                ? 1 : request.selectedRecordIds().size();
    }

    private Map<String, Object> requestValues(RecordSaveRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.fieldValues())
                ? Map.of() : request.fieldValues();
    }

    private String resolveTitle(Map<String, Object> values) {
        String title = valueAsString(values.get("title"));
        if (StringUtils.hasText(title)) {
            return title;
        }
        return values.values().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("未命名记录");
    }

    private List<FieldChangeView> fieldChanges(Map<String, Object> before, Map<String, Object> after) {
        return after.entrySet().stream()
                .map(entry -> new FieldChangeView(entry.getKey(), Objects.isNull(before) ? null : before.get(entry.getKey()),
                        entry.getValue(), "READABLE", "字段值已按当前权限快照写入。"))
                .toList();
    }

    private String requestKeyword(RecordSearchRequest request) {
        return Objects.isNull(request) ? null : request.keyword();
    }

    private String requestSceneCode(RecordSearchRequest request) {
        return safeText(Objects.isNull(request) ? null : request.sceneCode(), DEFAULT_SCENE_CODE);
    }

    private int pageNo(RecordSearchRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.pageNo()) || request.pageNo() <= 0
                ? 1 : request.pageNo();
    }

    private int pageSize(RecordSearchRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.pageSize()) || request.pageSize() <= 0
                ? 20 : request.pageSize();
    }

    private List<FieldFilterCriterion> safeFilters(RecordSearchRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.fieldFilters()) ? List.of() : request.fieldFilters();
    }

    private List<RecordSortCriterion> safeSorts(RecordSearchRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.sorts()) ? List.of() : request.sorts();
    }

    private List<String> valueList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    private String valueAsString(Object value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private String statusColor(String status) {
        return switch (safeText(status, STATUS_DRAFT)) {
            case "PENDING_APPROVAL" -> "ORANGE";
            case STATUS_ACTIVE -> "BLUE";
            case STATUS_ARCHIVED -> "RED";
            default -> "GRAY";
        };
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "JSON序列化失败");
        }
    }

    private String readJsonString(String json, String fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, String.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private List<String> readJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<FieldChangeView> readFieldChanges(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FIELD_CHANGE_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<FieldMaskResult> readFieldMasks(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FIELD_MASK_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
