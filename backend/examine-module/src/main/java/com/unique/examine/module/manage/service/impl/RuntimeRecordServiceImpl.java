package com.unique.examine.module.manage.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.flow.FlowRecordRuntimeService;
import com.unique.examine.core.flow.FlowRecordStartRequest;
import com.unique.examine.core.flow.FlowRecordStartResult;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.Action;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Menu;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.PublishVersion;
import com.unique.examine.module.base.entity.Record;
import com.unique.examine.module.base.entity.RecordHistory;
import com.unique.examine.module.base.entity.RecordIndex;
import com.unique.examine.module.base.entity.RecordRelation;
import com.unique.examine.module.base.entity.RecordUniqueIndex;
import com.unique.examine.module.base.entity.RecordValue;
import com.unique.examine.module.base.service.IActionService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IMenuService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordHistoryService;
import com.unique.examine.module.base.service.IRecordIndexService;
import com.unique.examine.module.base.service.IRecordRelationService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.base.service.IRecordUniqueIndexService;
import com.unique.examine.module.base.service.IRecordValueService;
import com.unique.examine.module.manage.bo.RecordFieldValueBO;
import com.unique.examine.module.manage.bo.RecordQueryBO;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.bo.RecordSubmitBO;
import com.unique.examine.module.manage.bo.RecordUpdateBO;
import com.unique.examine.module.manage.enums.ModuleConfigErrorCode;
import com.unique.examine.module.manage.enums.RuntimeRecordErrorCode;
import com.unique.examine.module.manage.service.RuntimeRecordService;
import com.unique.examine.module.manage.vo.ActionConfigVO;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordHistoryVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.module.manage.vo.RecordRelationVO;
import com.unique.examine.module.manage.vo.RecordValueVO;
import com.unique.examine.module.manage.vo.RuntimeMenuVO;
import com.unique.examine.module.manage.vo.RuntimeModuleSchemaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 运行台记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class RuntimeRecordServiceImpl implements RuntimeRecordService {

    private static final String ACTIVE_DELETE_MARKER = "0";

    private static final String ACTIVE_UNIQUE_MARKER = "ACTIVE";

    private static final String PUBLISHED = "PUBLISHED";

    private static final String DELETED = "DELETED";

    private static final String DRAFT = "DRAFT";

    private static final String SUBMITTED = "SUBMITTED";

    private static final String IN_APPROVAL = "IN_APPROVAL";

    private static final String ROOT_ROW = "ROOT";

    private static final byte YES = 1;

    private static final byte NO = 0;

    private static final Set<String> TEXT_TYPES = Set.of("TEXT", "TEXTAREA", "SELECT", "RADIO", "DICT", "SERIAL");

    private static final Set<String> NUMBER_TYPES = Set.of("NUMBER", "DECIMAL");

    private static final Set<String> JSON_TYPES = Set.of("MULTI_SELECT", "CHECKBOX", "RELATION", "SUB_TABLE",
            "ATTACHMENT", "IMAGE", "ADDRESS", "TAG", "JSON");

    private final IModelService modelService;

    private final IFieldService fieldService;

    private final IMenuService menuService;

    private final IActionService actionService;

    private final IPublishVersionService publishVersionService;

    private final IRecordService recordService;

    private final IRecordValueService recordValueService;

    private final IRecordHistoryService recordHistoryService;

    private final IRecordIndexService recordIndexService;

    private final IRecordUniqueIndexService recordUniqueIndexService;

    private final IRecordRelationService recordRelationService;

    private final PermissionService permissionService;

    private final ObjectMapper objectMapper;

    private final java.util.Optional<FlowRecordRuntimeService> flowRecordRuntimeService;

    /**
     * 查询运行台菜单。
     */
    @Override
    public List<RuntimeMenuVO> runtimeMenus(Long systemId) {
        permissionService.requireOperation("SYSTEM_MEMBER");
        List<Menu> menus = menuService.lambdaQuery()
                .eq(Menu::getSystemId, systemId)
                .eq(Menu::getMenuType, "RUNTIME")
                .eq(Menu::getVisibleFlag, YES)
                .eq(Menu::getEnabledFlag, YES)
                .eq(Menu::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list();
        Map<Long, RuntimeMenuVO> menuMap = menus.stream()
                .sorted(Comparator.comparing(Menu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Menu::getCode, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toMap(Menu::getMenuId, menu -> RuntimeMenuVO.builder()
                        .menuId(toId(menu.getMenuId()))
                        .parentId(toId(menu.getParentId()))
                        .appId(toId(menu.getAppId()))
                        .moduleId(toId(menu.getModuleId()))
                        .code(menu.getCode())
                        .name(menu.getName())
                        .routePath(menu.getRoutePath())
                        .icon(menu.getIcon())
                        .children(new ArrayList<>())
                        .build(), (left, right) -> left, LinkedHashMap::new));
        List<RuntimeMenuVO> roots = new ArrayList<>();
        menus.forEach(menu -> {
            RuntimeMenuVO current = menuMap.get(menu.getMenuId());
            RuntimeMenuVO parent = menuMap.get(menu.getParentId());
            if (Objects.nonNull(parent)) {
                parent.getChildren().add(current);
            } else {
                roots.add(current);
            }
        });
        return roots;
    }

    /**
     * 查询模块运行态 schema。
     */
    @Override
    public RuntimeModuleSchemaVO moduleSchema(Long systemId, Long moduleId) {
        permissionService.requireOperation("MENU_VISIBLE");
        Model model = activeRuntimeModule(systemId, moduleId);
        PublishVersion publishVersion = currentPublishVersion(model);
        JsonNode pageSnapshot = readJson(publishVersion.getPageSnapshotJson());
        return RuntimeModuleSchemaVO.builder()
                .moduleId(toId(moduleId))
                .moduleCode(model.getCode())
                .publishedVersionId(toId(publishVersion.getPublishVersionId()))
                .listSchema(pageSchema(pageSnapshot, "LIST"))
                .formSchema(pageSchema(pageSnapshot, "FORM"))
                .detailSchema(pageSchema(pageSnapshot, "DETAIL"))
                .fieldDefinitions(readJson(publishVersion.getFieldSnapshotJson()))
                .availableActions(availableActions(systemId, moduleId))
                .permissionHints(permissionService.currentPermission().getOperations().stream().toList())
                .statusRules(readJson(publishVersion.getFlowBindingSnapshotJson()))
                .build();
    }

    /**
     * 查询运行记录分页。
     */
    @Override
    public PageResult<RecordListItemVO> queryRecords(Long systemId, Long moduleId, RecordQueryBO queryBO) {
        permissionService.requireOperation("RECORD_VIEW");
        activeRuntimeModule(systemId, moduleId);
        List<Record> allRecords = recordService.lambdaQuery()
                .eq(Record::getSystemId, systemId)
                .eq(Record::getModuleId, moduleId)
                .isNull(Record::getDeletedAt)
                .list()
                .stream()
                .filter(record -> !DELETED.equals(record.getRecordStatus()))
                .filter(record -> !StringUtils.hasText(queryBO.getKeyword()) || contains(record.getTitle(), queryBO.getKeyword())
                        || contains(record.getRecordNo(), queryBO.getKeyword()))
                .sorted(Comparator.comparing(Record::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
        long pageNo = Optional.ofNullable(queryBO.getPageNo()).orElse(1L);
        long pageSize = Optional.ofNullable(queryBO.getPageSize()).orElse(20L);
        int from = Math.toIntExact(Math.min((pageNo - 1) * pageSize, allRecords.size()));
        int to = Math.toIntExact(Math.min(from + pageSize, allRecords.size()));
        List<ActionConfigVO> actions = availableActions(systemId, moduleId);
        List<RecordListItemVO> records = allRecords.subList(from, to).stream()
                .map(record -> toListItem(record, values(record.getRecordId()), actions))
                .toList();
        return PageResult.<RecordListItemVO>builder()
                .records(records)
                .total(allRecords.size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(to < allRecords.size())
                .build();
    }

    /**
     * 创建运行记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResultVO createRecord(Long systemId, Long moduleId, RecordSaveBO saveBO) {
        permissionService.requireOperation("RECORD_CREATE");
        Model model = activeRuntimeModule(systemId, moduleId);
        Map<String, Field> fields = activeFieldMap(systemId, moduleId);
        List<RecordValue> values = validateAndBuildValues(model, null, fields, saveBO.getValues());
        ensureUniqueAvailable(model, null, fields, values);
        LocalDateTime now = LocalDateTime.now();
        Record record = new Record()
                .setSystemId(systemId)
                .setTenantId(model.getTenantId())
                .setAppId(model.getAppId())
                .setModuleId(moduleId)
                .setPublishVersionId(model.getCurrentPublishVersionId())
                .setRecordStatus(DRAFT)
                .setLockedFlag(NO)
                .setRecordVersion(1)
                .setActiveUniqueMarker(ACTIVE_UNIQUE_MARKER)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId())
                .setCreatedAt(now)
                .setUpdatedAt(now);
        recordService.save(record);
        fillRecordValues(record, values);
        recordValueService.saveBatch(values);
        persistIndexes(record, fields, values);
        persistUniqueIndexes(record, fields, values);
        persistRelations(record, fields, values);
        record.setTitle(resolveTitle(model, values))
                .setRecordNo(resolveRecordNo(model, values, record.getRecordId()))
                .setUpdatedAt(LocalDateTime.now());
        recordService.updateById(record);
        saveHistory(record, "CREATE", null, record.getRecordStatus(), List.copyOf(fields.keySet()), null,
                valueSnapshot(values), saveBO.getRemark());
        return mutationResult(record, values.stream().map(RecordValue::getFieldCode).toList());
    }

    /**
     * 查询运行记录详情。
     */
    @Override
    public RecordDetailVO recordDetail(Long systemId, Long moduleId, Long recordId) {
        permissionService.requireOperation("RECORD_VIEW");
        Record record = activeRecord(systemId, moduleId, recordId);
        permissionService.requireDataScope("RECORD", toId(recordId), toId(record.getCreatedBy()));
        List<RecordValue> values = values(recordId);
        return RecordDetailVO.builder()
                .recordId(toId(recordId))
                .recordStatus(record.getRecordStatus())
                .recordVersion(record.getRecordVersion())
                .values(values.stream().map(this::toValueVO).toList())
                .fileRefs(List.of())
                .flowSummary(readJson(null))
                .historySummary(readJson(null))
                .availableActions(availableActions(systemId, moduleId))
                .fieldPermissions(List.of())
                .auditFields(auditFields(record))
                .build();
    }

    /**
     * 更新运行记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResultVO updateRecord(Long systemId, Long moduleId, Long recordId, RecordUpdateBO updateBO) {
        permissionService.requireOperation("RECORD_EDIT");
        Model model = activeRuntimeModule(systemId, moduleId);
        Record record = activeRecord(systemId, moduleId, recordId);
        permissionService.requireDataScope("RECORD", toId(recordId), toId(record.getCreatedBy()));
        ensureEditable(record, updateBO.getRecordVersion());
        Map<String, Field> fields = activeFieldMap(systemId, moduleId);
        List<RecordValue> beforeValues = values(recordId);
        List<RecordValue> newValues = validateAndBuildValues(model, recordId, fields, updateBO.getValues());
        ensureUniqueAvailable(model, recordId, fields, newValues);
        removeRecordChildren(recordId);
        fillRecordValues(record, newValues);
        recordValueService.saveBatch(newValues);
        persistIndexes(record, fields, newValues);
        persistUniqueIndexes(record, fields, newValues);
        persistRelations(record, fields, newValues);
        String beforeStatus = record.getRecordStatus();
        record.setRecordVersion(record.getRecordVersion() + 1)
                .setTitle(resolveTitle(model, newValues))
                .setRecordNo(resolveRecordNo(model, newValues, recordId))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        recordService.updateById(record);
        List<String> changedFields = changedFields(beforeValues, newValues);
        saveHistory(record, "UPDATE", beforeStatus, record.getRecordStatus(), changedFields, valueSnapshot(beforeValues),
                valueSnapshot(newValues), updateBO.getRemark());
        return mutationResult(record, changedFields);
    }

    /**
     * 软删除运行记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResultVO deleteRecord(Long systemId, Long moduleId, Long recordId) {
        permissionService.requireOperation("RECORD_DELETE");
        Record record = activeRecord(systemId, moduleId, recordId);
        permissionService.requireDataScope("RECORD", toId(recordId), toId(record.getCreatedBy()));
        if (Objects.equals(record.getLockedFlag(), YES)) {
            throw new BusinessException(RuntimeRecordErrorCode.RECORD_STATUS_CONFLICT);
        }
        String beforeStatus = record.getRecordStatus();
        LocalDateTime now = LocalDateTime.now();
        record.setRecordStatus(DELETED)
                .setActiveUniqueMarker(toId(recordId))
                .setDeletedBy(currentMemberId())
                .setDeletedAt(now)
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(now)
                .setRecordVersion(record.getRecordVersion() + 1);
        recordService.updateById(record);
        recordUniqueIndexService.lambdaUpdate()
                .eq(RecordUniqueIndex::getRecordId, recordId)
                .set(RecordUniqueIndex::getActiveUniqueMarker, toId(recordId))
                .set(RecordUniqueIndex::getUpdatedAt, now)
                .update();
        saveHistory(record, "DELETE", beforeStatus, record.getRecordStatus(), List.of(), valueSnapshot(values(recordId)),
                null, "删除运行记录");
        return mutationResult(record, List.of());
    }

    /**
     * 提交运行记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordMutationResultVO submitRecord(Long systemId, Long moduleId, Long recordId, RecordSubmitBO submitBO) {
        permissionService.requireOperation("RECORD_SUBMIT");
        Model model = activeRuntimeModule(systemId, moduleId);
        Record record = activeRecord(systemId, moduleId, recordId);
        permissionService.requireDataScope("RECORD", toId(recordId), toId(record.getCreatedBy()));
        ensureEditable(record, submitBO.getRecordVersion());
        String beforeStatus = record.getRecordStatus();
        FlowRecordStartResult flowStart = startFlowIfBound(model, record);
        String nextStatus = Objects.nonNull(flowStart) ? IN_APPROVAL : SUBMITTED;
        record.setRecordStatus(nextStatus)
                .setFlowStatus(nextStatus)
                .setFlowInstanceId(Objects.isNull(flowStart) ? record.getFlowInstanceId() : flowStart.getInstanceId())
                .setLockedFlag(Objects.nonNull(flowStart) ? YES : NO)
                .setRecordVersion(record.getRecordVersion() + 1)
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        recordService.updateById(record);
        saveHistory(record, "SUBMIT", beforeStatus, nextStatus, List.of(), null, valueSnapshot(values(recordId)),
                submitBO.getReason());
        return mutationResult(record, List.of());
    }

    private FlowRecordStartResult startFlowIfBound(Model model, Record record) {
        if (Objects.isNull(model.getFlowBindingId())) {
            return null;
        }
        return flowRecordRuntimeService
                .orElseThrow(() -> new BusinessException(RuntimeRecordErrorCode.FLOW_BINDING_MISSING))
                .startForRecord(FlowRecordStartRequest.builder()
                        .systemId(record.getSystemId())
                        .tenantId(record.getTenantId())
                        .moduleId(record.getModuleId())
                        .recordId(record.getRecordId())
                        .actionCode("RECORD_SUBMIT")
                        .starterMemberId(currentMemberId())
                        .requestId(currentRequestId())
                        .build());
    }

    /**
     * 查询记录历史。
     */
    @Override
    public List<RecordHistoryVO> recordHistory(Long systemId, Long moduleId, Long recordId) {
        permissionService.requireOperation("RECORD_HISTORY_VIEW");
        activeRecord(systemId, moduleId, recordId);
        return recordHistoryService.lambdaQuery()
                .eq(RecordHistory::getSystemId, systemId)
                .eq(RecordHistory::getModuleId, moduleId)
                .eq(RecordHistory::getRecordId, recordId)
                .orderByDesc(RecordHistory::getCreatedAt)
                .list()
                .stream()
                .map(this::toHistoryVO)
                .toList();
    }

    /**
     * 查询记录关联关系。
     */
    @Override
    public List<RecordRelationVO> recordRelations(Long systemId, Long moduleId, Long recordId) {
        permissionService.requireOperation("RECORD_VIEW");
        activeRecord(systemId, moduleId, recordId);
        return recordRelationService.lambdaQuery()
                .eq(RecordRelation::getSystemId, systemId)
                .eq(RecordRelation::getSourceModuleId, moduleId)
                .eq(RecordRelation::getSourceRecordId, recordId)
                .eq(RecordRelation::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .map(relation -> RecordRelationVO.builder()
                        .relationId(toId(relation.getRelationId()))
                        .fieldId(toId(relation.getFieldId()))
                        .targetModuleId(toId(relation.getTargetModuleId()))
                        .targetRecordId(toId(relation.getTargetRecordId()))
                        .relationType(relation.getRelationType())
                        .displaySnapshot(readJson(relation.getDisplaySnapshotJson()))
                        .build())
                .toList();
    }

    private Model activeRuntimeModule(Long systemId, Long moduleId) {
        Model model = modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getModuleId, moduleId)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(model)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_NOT_FOUND);
        }
        if (!PUBLISHED.equals(model.getModuleStatus()) || Objects.isNull(model.getCurrentPublishVersionId())) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        }
        return model;
    }

    private PublishVersion currentPublishVersion(Model model) {
        PublishVersion publishVersion = publishVersionService.lambdaQuery()
                .eq(PublishVersion::getPublishVersionId, model.getCurrentPublishVersionId())
                .eq(PublishVersion::getSystemId, model.getSystemId())
                .eq(PublishVersion::getModuleId, model.getModuleId())
                .one();
        if (Objects.isNull(publishVersion)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        }
        return publishVersion;
    }

    private Record activeRecord(Long systemId, Long moduleId, Long recordId) {
        Record record = recordService.lambdaQuery()
                .eq(Record::getSystemId, systemId)
                .eq(Record::getModuleId, moduleId)
                .eq(Record::getRecordId, recordId)
                .isNull(Record::getDeletedAt)
                .one();
        if (Objects.isNull(record) || DELETED.equals(record.getRecordStatus())) {
            throw new BusinessException(RuntimeRecordErrorCode.RECORD_NOT_FOUND);
        }
        return record;
    }

    private Map<String, Field> activeFieldMap(Long systemId, Long moduleId) {
        return fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .filter(field -> !DELETED.equals(field.getFieldStatus()))
                .sorted(Comparator.comparing(Field::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toMap(Field::getCode, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
    }

    private List<RecordValue> validateAndBuildValues(Model model, Long recordId, Map<String, Field> fields,
            List<RecordFieldValueBO> inputValues) {
        Map<String, RecordFieldValueBO> inputMap = CollectionUtils.isEmpty(inputValues) ? Map.of() : inputValues.stream()
                .collect(Collectors.toMap(RecordFieldValueBO::getFieldCode, Function.identity(), (left, right) -> right,
                        LinkedHashMap::new));
        fields.values().forEach(field -> {
            if (Objects.equals(field.getRequiredFlag(), YES) && isEmptyValue(inputMap.get(field.getCode()))) {
                throw new BusinessException(RuntimeRecordErrorCode.FIELD_REQUIRED_MISSING,
                        "必填字段缺失：" + field.getCode());
            }
        });
        List<RecordValue> values = new ArrayList<>();
        inputMap.forEach((fieldCode, input) -> {
            Field field = fields.get(fieldCode);
            if (Objects.isNull(field)) {
                throw new BusinessException(ModuleConfigErrorCode.FIELD_NOT_FOUND);
            }
            permissionService.requireFieldWritable(fieldCode);
            values.add(buildValue(model, recordId, field, input));
        });
        return values;
    }

    private RecordValue buildValue(Model model, Long recordId, Field field, RecordFieldValueBO input) {
        JsonNode value = Optional.ofNullable(input.getValue()).orElse(NullNode.getInstance());
        RecordValue recordValue = new RecordValue()
                .setSystemId(model.getSystemId())
                .setTenantId(model.getTenantId())
                .setModuleId(model.getModuleId())
                .setRecordId(recordId)
                .setFieldId(field.getFieldId())
                .setFieldCode(field.getCode())
                .setFieldType(field.getFieldType())
                .setRowKey(ROOT_ROW)
                .setDisplayValueJson(writeJson(Optional.ofNullable(input.getDisplayValue()).orElse(value)))
                .setValueSnapshotJson(writeJson(Optional.ofNullable(input.getDisplayValue()).orElse(value)))
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        fillTypedValue(recordValue, value);
        recordValue.setValueHash(hash(value.toString()));
        return recordValue;
    }

    private void fillTypedValue(RecordValue recordValue, JsonNode value) {
        try {
            String fieldType = recordValue.getFieldType();
            if (value.isNull()) {
                return;
            }
            if (TEXT_TYPES.contains(fieldType)) {
                recordValue.setValueText(value.isTextual() ? value.asText() : value.toString());
            } else if (NUMBER_TYPES.contains(fieldType)) {
                BigDecimal decimal = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
                recordValue.setValueNumber(decimal);
                recordValue.setValueText(decimal.toPlainString());
            } else if ("DATE".equals(fieldType)) {
                recordValue.setValueDate(LocalDate.parse(value.asText()));
                recordValue.setValueText(value.asText());
            } else if ("DATETIME".equals(fieldType)) {
                recordValue.setValueDatetime(LocalDateTime.parse(value.asText()));
                recordValue.setValueText(value.asText());
            } else if ("BOOLEAN".equals(fieldType)) {
                recordValue.setValueBool((byte) (value.asBoolean() ? 1 : 0));
                recordValue.setValueText(Boolean.toString(value.asBoolean()));
            } else if (JSON_TYPES.contains(fieldType)) {
                recordValue.setValueJson(value.toString());
                recordValue.setValueText(value.isValueNode() ? value.asText() : value.toString());
            } else {
                recordValue.setValueText(value.isTextual() ? value.asText() : value.toString());
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(RuntimeRecordErrorCode.FIELD_VALUE_TYPE_INVALID,
                    "字段值类型不合法：" + recordValue.getFieldCode());
        }
    }

    private void fillRecordValues(Record record, List<RecordValue> values) {
        LocalDateTime now = LocalDateTime.now();
        values.forEach(value -> value.setRecordId(record.getRecordId())
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private void ensureUniqueAvailable(Model model, Long currentRecordId, Map<String, Field> fields,
            List<RecordValue> values) {
        Map<String, RecordValue> valueMap = values.stream()
                .collect(Collectors.toMap(RecordValue::getFieldCode, Function.identity(), (left, right) -> right));
        fields.values().stream()
                .filter(field -> Objects.equals(field.getUniqueFlag(), YES))
                .forEach(field -> {
                    RecordValue value = valueMap.get(field.getCode());
                    if (Objects.isNull(value) || !StringUtils.hasText(value.getValueHash())) {
                        return;
                    }
                    List<RecordUniqueIndex> conflicts = recordUniqueIndexService.lambdaQuery()
                            .eq(RecordUniqueIndex::getSystemId, model.getSystemId())
                            .eq(RecordUniqueIndex::getModuleId, model.getModuleId())
                            .eq(RecordUniqueIndex::getConstraintCode, uniqueConstraintCode(field))
                            .eq(RecordUniqueIndex::getCombinedValueHash, value.getValueHash())
                            .eq(RecordUniqueIndex::getActiveUniqueMarker, ACTIVE_UNIQUE_MARKER)
                            .list();
                    boolean conflict = conflicts.stream()
                            .anyMatch(index -> !Objects.equals(index.getRecordId(), currentRecordId));
                    if (conflict) {
                        throw new BusinessException(RuntimeRecordErrorCode.FIELD_UNIQUE_CONFLICT,
                                "字段唯一值冲突：" + field.getCode());
                    }
                });
    }

    private void persistIndexes(Record record, Map<String, Field> fields, List<RecordValue> values) {
        List<RecordIndex> indexes = values.stream()
                .filter(value -> Objects.equals(fields.get(value.getFieldCode()).getIndexFlag(), YES))
                .map(value -> new RecordIndex()
                        .setSystemId(record.getSystemId())
                        .setTenantId(record.getTenantId())
                        .setModuleId(record.getModuleId())
                        .setRecordId(record.getRecordId())
                        .setFieldId(value.getFieldId())
                        .setFieldCode(value.getFieldCode())
                        .setRowKey(value.getRowKey())
                        .setIndexText(value.getValueText())
                        .setIndexNumber(value.getValueNumber())
                        .setIndexDatetime(value.getValueDatetime())
                        .setIndexDate(value.getValueDate())
                        .setIndexBool(value.getValueBool())
                        .setIndexHash(value.getValueHash())
                        .setRecordStatus(record.getRecordStatus())
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now()))
                .toList();
        if (!indexes.isEmpty()) {
            recordIndexService.saveBatch(indexes);
        }
    }

    private void persistUniqueIndexes(Record record, Map<String, Field> fields, List<RecordValue> values) {
        List<RecordUniqueIndex> indexes = values.stream()
                .filter(value -> Objects.equals(fields.get(value.getFieldCode()).getUniqueFlag(), YES))
                .map(value -> new RecordUniqueIndex()
                        .setSystemId(record.getSystemId())
                        .setTenantId(record.getTenantId())
                        .setModuleId(record.getModuleId())
                        .setConstraintCode(uniqueConstraintCode(fields.get(value.getFieldCode())))
                        .setFieldIdsJson(writeJson(List.of(toId(value.getFieldId()))))
                        .setFieldCodesJson(writeJson(List.of(value.getFieldCode())))
                        .setCombinedValueHash(value.getValueHash())
                        .setDisplayValuesJson(value.getDisplayValueJson())
                        .setRecordId(record.getRecordId())
                        .setActiveUniqueMarker(ACTIVE_UNIQUE_MARKER)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now()))
                .toList();
        if (!indexes.isEmpty()) {
            recordUniqueIndexService.saveBatch(indexes);
        }
    }

    private void persistRelations(Record record, Map<String, Field> fields, List<RecordValue> values) {
        List<RecordRelation> relations = values.stream()
                .filter(value -> "RELATION".equals(value.getFieldType()))
                .flatMap(value -> relationRecordIds(readJson(value.getValueJson())).stream()
                        .map(targetRecordId -> new RecordRelation()
                                .setSystemId(record.getSystemId())
                                .setTenantId(record.getTenantId())
                                .setSourceModuleId(record.getModuleId())
                                .setSourceRecordId(record.getRecordId())
                                .setFieldId(value.getFieldId())
                                .setRowKey(ROOT_ROW)
                                .setTargetModuleId(relationTargetModule(fields.get(value.getFieldCode())))
                                .setTargetRecordId(targetRecordId)
                                .setRelationType("FIELD")
                                .setDisplaySnapshotJson(value.getDisplayValueJson())
                                .setDeleteMarker(ACTIVE_DELETE_MARKER)
                                .setCreatedAt(LocalDateTime.now())
                                .setUpdatedAt(LocalDateTime.now())))
                .toList();
        if (!relations.isEmpty()) {
            recordRelationService.saveBatch(relations);
        }
    }

    private void removeRecordChildren(Long recordId) {
        recordValueService.lambdaUpdate().eq(RecordValue::getRecordId, recordId).remove();
        recordIndexService.lambdaUpdate().eq(RecordIndex::getRecordId, recordId).remove();
        recordUniqueIndexService.lambdaUpdate().eq(RecordUniqueIndex::getRecordId, recordId).remove();
        recordRelationService.lambdaUpdate()
                .eq(RecordRelation::getSourceRecordId, recordId)
                .set(RecordRelation::getDeleteMarker, toId(recordId))
                .update();
    }

    private List<RecordValue> values(Long recordId) {
        return recordValueService.lambdaQuery()
                .eq(RecordValue::getRecordId, recordId)
                .list();
    }

    private void ensureEditable(Record record, Integer requestVersion) {
        if (!Objects.equals(record.getRecordVersion(), requestVersion) || Objects.equals(record.getLockedFlag(), YES)
                || DELETED.equals(record.getRecordStatus())) {
            throw new BusinessException(RuntimeRecordErrorCode.RECORD_STATUS_CONFLICT);
        }
    }

    private String resolveTitle(Model model, List<RecordValue> values) {
        return values.stream()
                .filter(value -> Objects.equals(value.getFieldId(), model.getTitleFieldId()))
                .findFirst()
                .map(RecordValue::getValueText)
                .filter(StringUtils::hasText)
                .orElse("未命名记录");
    }

    private String resolveRecordNo(Model model, List<RecordValue> values, Long recordId) {
        return values.stream()
                .filter(value -> Objects.equals(value.getFieldId(), model.getRecordNoFieldId()))
                .findFirst()
                .map(RecordValue::getValueText)
                .filter(StringUtils::hasText)
                .orElse("REC-" + recordId);
    }

    private void saveHistory(Record record, String operationType, String beforeStatus, String afterStatus,
            List<String> changedFields, JsonNode beforeSnapshot, JsonNode afterSnapshot, String remark) {
        LocalDateTime now = LocalDateTime.now();
        recordHistoryService.save(new RecordHistory()
                .setSystemId(record.getSystemId())
                .setTenantId(record.getTenantId())
                .setModuleId(record.getModuleId())
                .setRecordId(record.getRecordId())
                .setRecordVersion(record.getRecordVersion())
                .setPublishVersionId(record.getPublishVersionId())
                .setOperationType(operationType)
                .setBeforeStatus(beforeStatus)
                .setAfterStatus(afterStatus)
                .setChangedFieldsJson(writeJson(changedFields))
                .setBeforeSnapshotJson(writeJson(beforeSnapshot))
                .setAfterSnapshotJson(writeJson(afterSnapshot))
                .setRequestId(currentRequestId())
                .setOperatorMemberId(currentMemberId())
                .setRemark(remark)
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private List<String> changedFields(List<RecordValue> beforeValues, List<RecordValue> afterValues) {
        Map<String, String> before = beforeValues.stream()
                .collect(Collectors.toMap(RecordValue::getFieldCode, RecordValue::getValueHash, (left, right) -> left));
        return afterValues.stream()
                .filter(value -> !Objects.equals(before.get(value.getFieldCode()), value.getValueHash()))
                .map(RecordValue::getFieldCode)
                .toList();
    }

    private JsonNode valueSnapshot(List<RecordValue> values) {
        Map<String, JsonNode> snapshot = values.stream()
                .collect(Collectors.toMap(RecordValue::getFieldCode, this::valueNode, (left, right) -> right,
                        LinkedHashMap::new));
        return readJson(writeJson(snapshot));
    }

    private RecordListItemVO toListItem(Record record, List<RecordValue> values, List<ActionConfigVO> actions) {
        return RecordListItemVO.builder()
                .recordId(toId(record.getRecordId()))
                .recordNo(record.getRecordNo())
                .moduleId(toId(record.getModuleId()))
                .title(record.getTitle())
                .recordStatus(record.getRecordStatus())
                .flowStatus(record.getFlowStatus())
                .values(values.stream().map(this::toValueVO).toList())
                .availableActions(actions)
                .createdByName(toId(record.getCreatedBy()))
                .updatedAt(record.getUpdatedAt())
                .recordVersion(record.getRecordVersion())
                .build();
    }

    private RecordMutationResultVO mutationResult(Record record, List<String> changedFields) {
        return RecordMutationResultVO.builder()
                .recordId(toId(record.getRecordId()))
                .recordStatus(record.getRecordStatus())
                .recordVersion(record.getRecordVersion())
                .flowInstanceId(toId(record.getFlowInstanceId()))
                .changedFields(changedFields)
                .idempotencyReplay(false)
                .availableActions(availableActions(record.getSystemId(), record.getModuleId()))
                .build();
    }

    private RecordValueVO toValueVO(RecordValue value) {
        return RecordValueVO.builder()
                .fieldId(toId(value.getFieldId()))
                .fieldCode(value.getFieldCode())
                .fieldType(value.getFieldType())
                .value(valueNode(value))
                .displayValue(readJson(value.getDisplayValueJson()))
                .build();
    }

    private RecordHistoryVO toHistoryVO(RecordHistory history) {
        return RecordHistoryVO.builder()
                .historyId(toId(history.getHistoryId()))
                .recordVersion(history.getRecordVersion())
                .operationType(history.getOperationType())
                .beforeStatus(history.getBeforeStatus())
                .afterStatus(history.getAfterStatus())
                .changedFields(readJson(history.getChangedFieldsJson()))
                .beforeSnapshot(readJson(history.getBeforeSnapshotJson()))
                .afterSnapshot(readJson(history.getAfterSnapshotJson()))
                .requestId(history.getRequestId())
                .operatorMemberId(toId(history.getOperatorMemberId()))
                .remark(history.getRemark())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private List<ActionConfigVO> availableActions(Long systemId, Long moduleId) {
        return actionService.lambdaQuery()
                .eq(Action::getSystemId, systemId)
                .eq(Action::getModuleId, moduleId)
                .eq(Action::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .eq(Action::getEnabledFlag, YES)
                .list()
                .stream()
                .sorted(Comparator.comparing(Action::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(action -> ActionConfigVO.builder()
                        .actionId(toId(action.getActionId()))
                        .moduleId(toId(action.getModuleId()))
                        .actionCode(action.getActionCode())
                        .actionName(action.getActionName())
                        .actionType(action.getActionType())
                        .danger(Objects.equals(action.getDangerFlag(), YES))
                        .confirmRequired(Objects.equals(action.getConfirmRequired(), YES))
                        .enabled(Objects.equals(action.getEnabledFlag(), YES))
                        .config(action.getConfigJson())
                        .sortOrder(action.getSortOrder())
                        .build())
                .toList();
    }

    private JsonNode pageSchema(JsonNode pageSnapshot, String pageType) {
        if (pageSnapshot.isArray()) {
            for (JsonNode item : pageSnapshot) {
                if (Objects.equals(pageType, item.path("pageType").asText())) {
                    return readJson(item.path("schema").asText("{}"));
                }
            }
        }
        return pageSnapshot.path(pageType).isMissingNode() ? readJson(null) : pageSnapshot.path(pageType);
    }

    private JsonNode valueNode(RecordValue value) {
        if (StringUtils.hasText(value.getValueJson())) {
            return readJson(value.getValueJson());
        }
        if (Objects.nonNull(value.getValueNumber())) {
            return DecimalNode.valueOf(value.getValueNumber());
        }
        if (Objects.nonNull(value.getValueDate())) {
            return TextNode.valueOf(value.getValueDate().toString());
        }
        if (Objects.nonNull(value.getValueDatetime())) {
            return TextNode.valueOf(value.getValueDatetime().toString());
        }
        if (Objects.nonNull(value.getValueBool())) {
            return BooleanNode.valueOf(Objects.equals(value.getValueBool(), YES));
        }
        return StringUtils.hasText(value.getValueText()) ? TextNode.valueOf(value.getValueText()) : NullNode.getInstance();
    }

    private JsonNode auditFields(Record record) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("createdBy", toId(record.getCreatedBy()));
        audit.put("updatedBy", toId(record.getUpdatedBy()));
        audit.put("createdAt", record.getCreatedAt());
        audit.put("updatedAt", record.getUpdatedAt());
        return readJson(writeJson(audit));
    }

    private boolean isEmptyValue(RecordFieldValueBO value) {
        return Objects.isNull(value) || Objects.isNull(value.getValue()) || value.getValue().isNull()
                || value.getValue().isMissingNode()
                || (value.getValue().isTextual() && !StringUtils.hasText(value.getValue().asText()))
                || (value.getValue().isArray() && value.getValue().isEmpty());
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private String uniqueConstraintCode(Field field) {
        return "FIELD:" + field.getFieldId();
    }

    private List<Long> relationRecordIds(JsonNode value) {
        if (value.isArray()) {
            List<Long> ids = new ArrayList<>();
            value.forEach(item -> ids.add(item.isObject() ? item.path("recordId").asLong() : item.asLong()));
            return ids.stream().filter(id -> id > 0).toList();
        }
        if (value.isObject() && value.path("recordId").asLong() > 0) {
            return List.of(value.path("recordId").asLong());
        }
        if (value.isIntegralNumber()) {
            return List.of(value.asLong());
        }
        return List.of();
    }

    private Long relationTargetModule(Field field) {
        JsonNode config = readJson(field.getRelationConfigJson());
        return config.path("targetModuleId").isMissingNode() ? null : config.path("targetModuleId").asLong();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return TextNode.valueOf(json);
        }
    }

    private String writeJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(RuntimeRecordErrorCode.FIELD_VALUE_TYPE_INVALID);
        }
    }

    private Long currentMemberId() {
        RequestContext context = RequestContextHolder.get();
        if (Objects.isNull(context) || !StringUtils.hasText(context.getMemberId())) {
            return null;
        }
        return Long.valueOf(context.getMemberId());
    }

    private String currentRequestId() {
        RequestContext context = RequestContextHolder.get();
        return Objects.isNull(context) ? null : context.getRequestId();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String toId(Long id) {
        return Objects.isNull(id) ? null : id.toString();
    }
}
