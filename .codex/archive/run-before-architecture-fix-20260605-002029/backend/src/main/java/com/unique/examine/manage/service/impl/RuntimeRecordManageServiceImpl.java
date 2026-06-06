package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.BusinessRecord;
import com.unique.examine.base.entity.DataDictionary;
import com.unique.examine.base.entity.Department;
import com.unique.examine.base.entity.DictionaryItem;
import com.unique.examine.base.entity.FieldOption;
import com.unique.examine.base.entity.Module;
import com.unique.examine.base.entity.ModuleField;
import com.unique.examine.base.entity.RecordComment;
import com.unique.examine.base.entity.RecordUniqueValue;
import com.unique.examine.base.entity.RecordValue;
import com.unique.examine.base.entity.SerialSequence;
import com.unique.examine.base.entity.SystemMember;
import com.unique.examine.base.mapper.SerialSequenceMapper;
import com.unique.examine.base.service.IBusinessRecordService;
import com.unique.examine.base.service.IDataDictionaryService;
import com.unique.examine.base.service.IDepartmentService;
import com.unique.examine.base.service.IDictionaryItemService;
import com.unique.examine.base.service.IFieldOptionService;
import com.unique.examine.base.service.IModuleFieldService;
import com.unique.examine.base.service.IModuleService;
import com.unique.examine.base.service.IRecordCommentService;
import com.unique.examine.base.service.IRecordUniqueValueService;
import com.unique.examine.base.service.IRecordValueService;
import com.unique.examine.base.service.ISerialSequenceService;
import com.unique.examine.base.service.ISystemMemberService;
import com.unique.examine.manage.bo.BusinessRecordSaveBO;
import com.unique.examine.manage.bo.RecordCommentSaveBO;
import com.unique.examine.manage.dto.FieldValueDTO;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.service.RuntimeRecordManageService;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.RecordVO;
import com.unique.examine.manage.vo.SimpleVO;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuntimeRecordManageServiceImpl implements RuntimeRecordManageService {
    private final IBusinessRecordService recordService;
    private final IRecordValueService valueService;
    private final IRecordUniqueValueService uniqueValueService;
    private final IRecordCommentService commentService;
    private final IModuleService moduleService;
    private final IModuleFieldService fieldService;
    private final IFieldOptionService fieldOptionService;
    private final IDataDictionaryService dictionaryService;
    private final IDictionaryItemService dictionaryItemService;
    private final IDepartmentService departmentService;
    private final ISystemMemberService systemMemberService;
    private final ISerialSequenceService sequenceService;
    private final SerialSequenceMapper sequenceMapper;
    private final PermissionService permissionService;
    private final EntityMapConverter converter;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<RecordVO> records(long pageNo, long pageSize, Long systemId, Long tenantId, Long appId, Long moduleId, String recordNo, String status) {
        permissionService.requireAction(systemId, tenantId, "record:view");
        IPage<BusinessRecord> page = recordService.page(Page.of(pageNo, pageSize), Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, systemId).eq(BusinessRecord::getTenantId, tenantId)
                .eq(appId != null, BusinessRecord::getAppId, appId)
                .eq(BusinessRecord::getModuleId, moduleId)
                .like(recordNo != null && !recordNo.isBlank(), BusinessRecord::getRecordNo, recordNo)
                .eq(status != null && !status.isBlank(), BusinessRecord::getRecordStatus, status)
                .eq(BusinessRecord::getIsDeleted, 0).orderByDesc(BusinessRecord::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(this::toRecordVO).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordVO create(BusinessRecordSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "record:create");
        return createInternal(bo, SecurityContext.currentUser().getAccountId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordVO createByOpenApi(BusinessRecordSaveBO bo) {
        return createInternal(bo, 0L);
    }

    private RecordVO createInternal(BusinessRecordSaveBO bo, Long operatorId) {
        Module module = moduleService.getById(bo.getModuleId());
        if (module == null || !module.getSystemId().equals(bo.getSystemId()) || !module.getTenantId().equals(bo.getTenantId()) || !module.getAppId().equals(bo.getAppId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模块不存在或归属不一致");
        }
        List<ModuleField> fields = enabledFields(bo.getModuleId());
        Map<Long, FieldValueDTO> valueMap = normalizeValues(bo.getValues());
        validateRequired(fields, valueMap, StatusEnums.SUBMITTED.equals(bo.getRecordStatus()));
        validateValues(bo.getSystemId(), bo.getTenantId(), bo.getModuleId(), fields, valueMap, null);
        BusinessRecord record = new BusinessRecord();
        record.setSystemId(bo.getSystemId()); record.setTenantId(bo.getTenantId()); record.setAppId(bo.getAppId()); record.setModuleId(bo.getModuleId());
        record.setRecordNo((bo.getRecordNo() == null || bo.getRecordNo().isBlank()) ? nextRecordNo(bo.getSystemId(), bo.getTenantId(), bo.getModuleId()) : bo.getRecordNo());
        record.setRecordStatus(bo.getRecordStatus() == null ? StatusEnums.DRAFT : bo.getRecordStatus()); record.setProcessStatus(StatusEnums.NONE);
        record.setAppVersionId(bo.getAppVersionId()); record.setConfigSnapshot(bo.getConfigSnapshot()); record.setIsDeleted(0);
        record.setCreatedBy(operatorId); record.setUpdatedBy(operatorId);
        recordService.save(record);
        saveValues(record, fields, valueMap);
        return toRecordVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordVO update(Long recordId, BusinessRecordSaveBO bo) {
        BusinessRecord record = mustRecord(recordId);
        permissionService.requireAction(record.getSystemId(), record.getTenantId(), "record:update");
        List<ModuleField> fields = enabledFields(record.getModuleId());
        Map<Long, FieldValueDTO> valueMap = normalizeValues(bo.getValues());
        validateRequired(fields, valueMap, StatusEnums.SUBMITTED.equals(bo.getRecordStatus()));
        validateValues(record.getSystemId(), record.getTenantId(), record.getModuleId(), fields, valueMap, recordId);
        record.setRecordStatus(bo.getRecordStatus() == null ? record.getRecordStatus() : bo.getRecordStatus());
        record.setConfigSnapshot(bo.getConfigSnapshot() == null ? record.getConfigSnapshot() : bo.getConfigSnapshot());
        record.setUpdatedBy(SecurityContext.currentUser().getAccountId());
        recordService.updateById(record);
        valueService.remove(Wrappers.<RecordValue>lambdaQuery().eq(RecordValue::getRecordId, recordId));
        uniqueValueService.remove(Wrappers.<RecordUniqueValue>lambdaQuery().eq(RecordUniqueValue::getRecordId, recordId));
        saveValues(record, fields, valueMap);
        return toRecordVO(record);
    }

    @Override
    public RecordVO detail(Long recordId) {
        BusinessRecord record = mustRecord(recordId);
        permissionService.requireAction(record.getSystemId(), record.getTenantId(), "record:view");
        return toRecordVO(record);
    }

    @Override
    public RecordVO detailByOpenApi(Long recordId) {
        return toRecordVO(mustRecord(recordId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long recordId) {
        BusinessRecord record = mustRecord(recordId);
        permissionService.requireAction(record.getSystemId(), record.getTenantId(), "record:delete");
        record.setIsDeleted(1); record.setRecordStatus(StatusEnums.DELETED); record.setUpdatedBy(SecurityContext.currentUser().getAccountId());
        recordService.updateById(record);
        uniqueValueService.remove(Wrappers.<RecordUniqueValue>lambdaQuery().eq(RecordUniqueValue::getRecordId, recordId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO comment(RecordCommentSaveBO bo) {
        BusinessRecord record = mustRecord(bo.getRecordId());
        permissionService.requireAction(record.getSystemId(), record.getTenantId(), "record:comment");
        if (bo.getSystemId() != null && !bo.getSystemId().equals(record.getSystemId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "评论系统上下文与记录归属不一致");
        }
        if (bo.getTenantId() != null && !bo.getTenantId().equals(record.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "评论租户上下文与记录归属不一致");
        }
        String commentText = bo.getCommentText();
        if (commentText == null || commentText.isBlank()) {
            commentText = bo.getComment();
        }
        if (commentText == null || commentText.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论内容不能为空");
        }
        RecordComment comment = new RecordComment();
        comment.setSystemId(record.getSystemId()); comment.setTenantId(record.getTenantId()); comment.setRecordId(bo.getRecordId());
        comment.setCommentContent(commentText); comment.setCreatedBy(SecurityContext.currentUser().getAccountId());
        commentService.save(comment);
        return converter.toSimple(comment);
    }

    private void saveValues(BusinessRecord record, List<ModuleField> fields, Map<Long, FieldValueDTO> valueMap) {
        Map<Long, ModuleField> fieldMap = fields.stream().collect(Collectors.toMap(ModuleField::getId, Function.identity()));
        for (FieldValueDTO dto : valueMap.values()) {
            ModuleField field = fieldMap.get(dto.getFieldId());
            if (field == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "字段不属于当前模块：" + dto.getFieldId()); }
            RecordValue value = new RecordValue();
            value.setSystemId(record.getSystemId()); value.setTenantId(record.getTenantId()); value.setModuleId(record.getModuleId()); value.setRecordId(record.getId()); value.setFieldId(field.getId());
            applyTypedValue(value, field.getFieldType(), dto.getValue());
            valueService.save(value);
            if (Integer.valueOf(1).equals(field.getUniqueFlag()) && dto.getValue() != null && !dto.getValue().isBlank()) {
                RecordUniqueValue unique = new RecordUniqueValue();
                unique.setSystemId(record.getSystemId()); unique.setTenantId(record.getTenantId()); unique.setModuleId(record.getModuleId()); unique.setRecordId(record.getId()); unique.setFieldId(field.getId());
                unique.setValueHash(sha256(dto.getValue().trim())); unique.setIsDeleted(0); uniqueValueService.save(unique);
            }
        }
    }

    private void applyTypedValue(RecordValue value, String fieldType, String rawValue) {
        if (rawValue == null) { return; }
        switch (fieldType) {
            case "NUMBER", "AMOUNT" -> value.setNumberValue(parseDecimal(rawValue, fieldType));
            case "DATE", "DATETIME" -> value.setDatetimeValue(parseDateTime(rawValue, fieldType));
            case "BOOLEAN" -> value.setBooleanValue(Boolean.parseBoolean(rawValue) || "1".equals(rawValue) ? 1 : 0);
            case "MULTI_SELECT", "ATTACHMENT", "SUB_TABLE", "FORMULA" -> value.setJsonValue(rawValue);
            default -> value.setStringValue(rawValue);
        }
    }

    private void validateValues(Long systemId, Long tenantId, Long moduleId, List<ModuleField> fields, Map<Long, FieldValueDTO> valueMap, Long currentRecordId) {
        Map<Long, ModuleField> fieldMap = fields.stream().collect(Collectors.toMap(ModuleField::getId, Function.identity()));
        for (FieldValueDTO dto : valueMap.values()) {
            ModuleField field = fieldMap.get(dto.getFieldId());
            if (field == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "字段不属于当前模块：" + dto.getFieldId());
            }
            String rawValue = dto.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            validateTypedValue(systemId, tenantId, moduleId, field, rawValue);
            if (Integer.valueOf(1).equals(field.getUniqueFlag())) {
                validateUniqueValue(systemId, tenantId, moduleId, field, rawValue, currentRecordId);
            }
        }
    }

    private void validateTypedValue(Long systemId, Long tenantId, Long moduleId, ModuleField field, String rawValue) {
        try {
            switch (field.getFieldType()) {
                case "TEXT", "LONG_TEXT", "AUTO_NUMBER", "READONLY" -> { }
                case "NUMBER", "AMOUNT" -> parseDecimal(rawValue, field.getFieldName());
                case "DATE", "DATETIME" -> parseDateTime(rawValue, field.getFieldName());
                case "BOOLEAN" -> validateBoolean(field, rawValue);
                case "SINGLE_SELECT" -> validateOptions(field, List.of(rawValue));
                case "MULTI_SELECT" -> validateOptions(field, parseMultiValue(rawValue));
                case "DICTIONARY" -> validateDictionary(systemId, tenantId, field, rawValue);
                case "DEPARTMENT" -> validateDepartments(systemId, tenantId, field, rawValue);
                case "MEMBER" -> validateMembers(systemId, tenantId, field, rawValue);
                case "RELATION_RECORD" -> validateRelationRecords(systemId, tenantId, moduleId, field, rawValue);
                case "ATTACHMENT", "SUB_TABLE", "FORMULA" -> objectMapper.readTree(rawValue);
                default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的字段类型：" + field.getFieldType());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字段值格式错误：" + field.getFieldName());
        }
    }

    private void validateUniqueValue(Long systemId, Long tenantId, Long moduleId, ModuleField field, String rawValue, Long currentRecordId) {
        String valueHash = sha256(rawValue.trim());
        long count = uniqueValueService.count(Wrappers.<RecordUniqueValue>lambdaQuery()
                .eq(RecordUniqueValue::getSystemId, systemId)
                .eq(RecordUniqueValue::getTenantId, tenantId)
                .eq(RecordUniqueValue::getModuleId, moduleId)
                .eq(RecordUniqueValue::getFieldId, field.getId())
                .eq(RecordUniqueValue::getValueHash, valueHash)
                .eq(RecordUniqueValue::getIsDeleted, 0)
                .ne(currentRecordId != null, RecordUniqueValue::getRecordId, currentRecordId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "唯一字段值已存在：" + field.getFieldName());
        }
    }

    private BigDecimal parseDecimal(String rawValue, String fieldName) {
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "数字字段格式错误：" + fieldName);
        }
    }

    private LocalDateTime parseDateTime(String rawValue, String fieldName) {
        try {
            if (rawValue.length() == 10) {
                return LocalDate.parse(rawValue).atStartOfDay();
            }
            return LocalDateTime.parse(rawValue.replace(" ", "T"));
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日期时间字段格式错误：" + fieldName);
        }
    }

    private void validateBoolean(ModuleField field, String rawValue) {
        if (!List.of("true", "false", "1", "0").contains(rawValue.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "布尔字段格式错误：" + field.getFieldName());
        }
    }

    private void validateOptions(ModuleField field, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        long count = fieldOptionService.count(Wrappers.<FieldOption>lambdaQuery()
                .eq(FieldOption::getFieldId, field.getId())
                .eq(FieldOption::getStatus, StatusEnums.ENABLED)
                .in(FieldOption::getOptionValue, values));
        if (count != values.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字段选项不存在或已停用：" + field.getFieldName());
        }
    }

    private void validateDictionary(Long systemId, Long tenantId, ModuleField field, String rawValue) {
        if (field.getEnumSource() == null || field.getEnumSource().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典字段未配置来源：" + field.getFieldName());
        }
        DataDictionary dictionary = dictionaryService.getOne(Wrappers.<DataDictionary>lambdaQuery()
                .eq(DataDictionary::getSystemId, systemId)
                .eq(DataDictionary::getTenantId, tenantId)
                .eq(DataDictionary::getDictCode, field.getEnumSource())
                .eq(DataDictionary::getStatus, StatusEnums.ENABLED), false);
        if (dictionary == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典来源不存在或已停用：" + field.getFieldName());
        }
        List<String> values = parseMultiValue(rawValue);
        long count = dictionaryItemService.count(Wrappers.<DictionaryItem>lambdaQuery()
                .eq(DictionaryItem::getDictId, dictionary.getId())
                .eq(DictionaryItem::getStatus, StatusEnums.ENABLED)
                .in(DictionaryItem::getItemValue, values));
        if (count != values.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典项不存在或已停用：" + field.getFieldName());
        }
    }

    private void validateDepartments(Long systemId, Long tenantId, ModuleField field, String rawValue) {
        List<Long> ids = parseIdValues(rawValue, field);
        if (ids.isEmpty()) {
            return;
        }
        long count = departmentService.count(Wrappers.<Department>lambdaQuery()
                .eq(Department::getSystemId, systemId)
                .eq(Department::getTenantId, tenantId)
                .eq(Department::getStatus, StatusEnums.ENABLED)
                .in(Department::getId, ids));
        if (count != ids.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门不存在或无权引用：" + field.getFieldName());
        }
    }

    private void validateMembers(Long systemId, Long tenantId, ModuleField field, String rawValue) {
        List<Long> ids = parseIdValues(rawValue, field);
        if (ids.isEmpty()) {
            return;
        }
        long count = systemMemberService.count(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId)
                .eq(SystemMember::getTenantId, tenantId)
                .eq(SystemMember::getStatus, StatusEnums.ENABLED)
                .in(SystemMember::getAccountId, ids));
        if (count != ids.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "成员不存在或无权引用：" + field.getFieldName());
        }
    }

    private void validateRelationRecords(Long systemId, Long tenantId, Long moduleId, ModuleField field, String rawValue) {
        List<Long> ids = parseIdValues(rawValue, field);
        if (ids.isEmpty()) {
            return;
        }
        long count = recordService.count(Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, systemId)
                .eq(BusinessRecord::getTenantId, tenantId)
                .eq(BusinessRecord::getModuleId, moduleId)
                .eq(BusinessRecord::getIsDeleted, 0)
                .in(BusinessRecord::getId, ids));
        if (count != ids.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关联记录不存在或无权引用：" + field.getFieldName());
        }
    }

    private List<String> parseMultiValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyList();
        }
        String value = rawValue.trim();
        if (value.startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(value);
                List<String> values = new ArrayList<>();
                node.forEach(item -> values.add(item.asText()));
                return values;
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "多值字段JSON格式错误");
            }
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private List<Long> parseIdValues(String rawValue, ModuleField field) {
        try {
            return parseMultiValue(rawValue).stream().map(Long::valueOf).toList();
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "引用字段ID格式错误：" + field.getFieldName());
        }
    }

    private String nextRecordNo(Long systemId, Long tenantId, Long moduleId) {
        SerialSequence sequence = sequenceMapper.selectForUpdate(systemId, tenantId, moduleId, "RECORD_NO");
        if (sequence == null) {
            sequence = new SerialSequence();
            sequence.setSystemId(systemId); sequence.setTenantId(tenantId); sequence.setModuleId(moduleId); sequence.setSequenceKey("RECORD_NO"); sequence.setPrefixRule("R"); sequence.setNextValue(2L); sequence.setStepValue(1); sequence.setStatus(StatusEnums.ENABLED);
            sequenceService.save(sequence);
            return "R000001";
        }
        long current = sequence.getNextValue();
        sequence.setNextValue(current + sequence.getStepValue());
        sequenceService.updateById(sequence);
        return String.format("%s%06d", sequence.getPrefixRule() == null ? "R" : sequence.getPrefixRule(), current);
    }

    private List<ModuleField> enabledFields(Long moduleId) {
        return fieldService.list(Wrappers.<ModuleField>lambdaQuery().eq(ModuleField::getModuleId, moduleId).eq(ModuleField::getStatus, StatusEnums.ENABLED));
    }

    private Map<Long, FieldValueDTO> normalizeValues(List<FieldValueDTO> values) {
        Map<Long, FieldValueDTO> map = new HashMap<>();
        if (values != null) { values.forEach(v -> map.put(v.getFieldId(), v)); }
        return map;
    }

    private void validateRequired(List<ModuleField> fields, Map<Long, FieldValueDTO> valueMap, boolean strict) {
        if (!strict) { return; }
        for (ModuleField field : fields) {
            FieldValueDTO value = valueMap.get(field.getId());
            if (Integer.valueOf(1).equals(field.getRequiredFlag()) && (value == null || value.getValue() == null || value.getValue().isBlank())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "必填字段缺失：" + field.getFieldName());
            }
        }
    }

    private BusinessRecord mustRecord(Long recordId) {
        BusinessRecord record = recordService.getById(recordId);
        if (record == null || Integer.valueOf(1).equals(record.getIsDeleted())) { throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在"); }
        return record;
    }

    private RecordVO toRecordVO(BusinessRecord record) {
        RecordVO vo = new RecordVO();
        vo.setId(record.getId()); vo.setSystemId(record.getSystemId()); vo.setTenantId(record.getTenantId()); vo.setAppId(record.getAppId()); vo.setModuleId(record.getModuleId());
        vo.setRecordNo(record.getRecordNo()); vo.setRecordStatus(record.getRecordStatus()); vo.setProcessStatus(record.getProcessStatus()); vo.setAppVersionId(record.getAppVersionId()); vo.setConfigSnapshot(record.getConfigSnapshot());
        vo.setIsDeleted(record.getIsDeleted()); vo.setCreatedBy(record.getCreatedBy()); vo.setUpdatedBy(record.getUpdatedBy()); vo.setCreatedAt(record.getCreatedAt()); vo.setUpdatedAt(record.getUpdatedAt());
        Map<Long, String> values = new HashMap<>();
        valueService.list(Wrappers.<RecordValue>lambdaQuery().eq(RecordValue::getRecordId, record.getId())).forEach(v -> values.put(v.getFieldId(), firstNonNull(v.getStringValue(), v.getNumberValue(), v.getDatetimeValue(), v.getBooleanValue(), v.getJsonValue())));
        vo.setValues(values);
        return vo;
    }

    private String firstNonNull(Object... values) {
        for (Object value : values) { if (value != null) { return String.valueOf(value); } }
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) { builder.append(String.format("%02x", b)); }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "唯一值摘要生成失败");
        }
    }
}
