package com.unique.examine.module.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.base.entity.ExportJob;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.FieldOption;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.Page;
import com.unique.examine.module.base.entity.Record;
import com.unique.examine.module.base.entity.RecordValue;
import com.unique.examine.module.base.service.IExportJobService;
import com.unique.examine.module.base.service.IFieldOptionService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPageService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.base.service.IRecordValueService;
import com.unique.examine.module.manage.bo.ModuleExportJobBO;
import com.unique.examine.module.manage.bo.ModuleFieldOptionDTO;
import com.unique.examine.module.manage.bo.ModuleFieldSaveBO;
import com.unique.examine.module.manage.bo.ModuleModelSaveBO;
import com.unique.examine.module.manage.bo.ModulePageSaveBO;
import com.unique.examine.module.manage.bo.ModuleRecordSaveBO;
import com.unique.examine.module.manage.bo.ModuleStatusBO;
import com.unique.examine.module.manage.converter.ModuleManageConverter;
import com.unique.examine.module.manage.enums.ModuleManageErrorCode;
import com.unique.examine.module.manage.service.ModuleManageService;
import com.unique.examine.module.manage.vo.ModuleManageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 动态模块管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class ModuleManageServiceImpl implements ModuleManageService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DISABLED = "DISABLED";
    private static final String ACTIVE = "ACTIVE";
    private static final String PENDING = "PENDING";
    private static final byte YES = 1;
    private static final int RECORD_NO_MAX_LENGTH = 64;

    private final IModelService modelService;
    private final IFieldService fieldService;
    private final IFieldOptionService fieldOptionService;
    private final IPageService pageService;
    private final IRecordService recordService;
    private final IRecordValueService recordValueService;
    private final IExportJobService exportJobService;

    @Override
    public List<ModuleManageVO> listModels(Long appId) {
        return modelService.list(Wrappers.<Model>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(appId), Model::getAppId, appId))
                .stream().map(ModuleManageConverter::fromModel).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO createModel(ModuleModelSaveBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getSystemId(), "systemId");
        requireId(bo.getAppId(), "appId");
        requireText(bo.getModuleCode(), "moduleCode");
        requireText(bo.getModuleName(), "moduleName");
        Model model = new Model();
        model.setTenantId(bo.getTenantId());
        model.setSystemId(bo.getSystemId());
        model.setAppId(bo.getAppId());
        model.setModuleCode(bo.getModuleCode());
        model.setModuleName(bo.getModuleName());
        model.setDataScopeType(StrUtil.blankToDefault(bo.getDataScopeType(), "OWNER"));
        model.setFlowEnabled(ObjectUtil.defaultIfNull(bo.getFlowEnabled(), (byte) 0));
        model.setImportEnabled(ObjectUtil.defaultIfNull(bo.getImportEnabled(), (byte) 0));
        model.setExportEnabled(ObjectUtil.defaultIfNull(bo.getExportEnabled(), YES));
        model.setStatus(DRAFT);
        fillAudit(model::setCreatedBy, model::setUpdatedBy);
        modelService.save(model);
        return ModuleManageConverter.fromModel(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO updateModelStatus(Long id, ModuleStatusBO bo) {
        Model model = requireModel(id);
        if (!DRAFT.equals(bo.getStatus()) && !PUBLISHED.equals(bo.getStatus()) && !DISABLED.equals(bo.getStatus())) {
            throwError(ModuleManageErrorCode.STATUS_INVALID);
        }
        model.setStatus(bo.getStatus());
        fillUpdatedBy(model::setUpdatedBy);
        modelService.updateById(model);
        return ModuleManageConverter.fromModel(model);
    }

    @Override
    public List<ModuleManageVO> listFields(Long moduleId) {
        requireId(moduleId, "moduleId");
        return fieldService.list(Wrappers.<Field>lambdaQuery()
                        .eq(Field::getModuleId, moduleId)
                        .orderByAsc(Field::getSortOrder))
                .stream().map(ModuleManageConverter::fromField).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO createField(ModuleFieldSaveBO bo) {
        requireId(bo.getModuleId(), "moduleId");
        requireText(bo.getFieldCode(), "fieldCode");
        requireText(bo.getFieldName(), "fieldName");
        Field field = new Field();
        field.setModuleId(bo.getModuleId());
        field.setFieldCode(bo.getFieldCode());
        field.setFieldName(bo.getFieldName());
        field.setFieldType(StrUtil.blankToDefault(bo.getFieldType(), "TEXT"));
        field.setRequiredFlag(ObjectUtil.defaultIfNull(bo.getRequiredFlag(), (byte) 0));
        field.setUniqueFlag(ObjectUtil.defaultIfNull(bo.getUniqueFlag(), (byte) 0));
        field.setListVisible(ObjectUtil.defaultIfNull(bo.getListVisible(), YES));
        field.setSearchable(ObjectUtil.defaultIfNull(bo.getSearchable(), (byte) 0));
        field.setEditable(ObjectUtil.defaultIfNull(bo.getEditable(), YES));
        field.setDefaultValue(bo.getDefaultValue());
        field.setValidationJson(bo.getValidationJson());
        field.setSortOrder(ObjectUtil.defaultIfNull(bo.getSortOrder(), 0));
        fillAudit(field::setCreatedBy, field::setUpdatedBy);
        fieldService.save(field);
        if (CollUtil.isNotEmpty(bo.getOptions())) {
            saveFieldOptions(field.getId(), bo.getOptions());
        }
        return ModuleManageConverter.fromField(field);
    }

    @Override
    public List<ModuleManageVO> listPages(Long moduleId) {
        requireId(moduleId, "moduleId");
        return pageService.list(Wrappers.<Page>lambdaQuery().eq(Page::getModuleId, moduleId))
                .stream().map(ModuleManageConverter::fromPage).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO createPage(ModulePageSaveBO bo) {
        requireId(bo.getModuleId(), "moduleId");
        requireText(bo.getPageCode(), "pageCode");
        requireText(bo.getPageName(), "pageName");
        Page page = new Page();
        page.setModuleId(bo.getModuleId());
        page.setPageCode(bo.getPageCode());
        page.setPageName(bo.getPageName());
        page.setPageType(StrUtil.blankToDefault(bo.getPageType(), "LIST"));
        page.setLayoutJson(bo.getLayoutJson());
        page.setButtonJson(bo.getButtonJson());
        page.setStatus(PUBLISHED);
        fillAudit(page::setCreatedBy, page::setUpdatedBy);
        pageService.save(page);
        return ModuleManageConverter.fromPage(page);
    }

    @Override
    public List<ModuleManageVO> listRecords(Long moduleId) {
        requireId(moduleId, "moduleId");
        return recordService.list(Wrappers.<Record>lambdaQuery().eq(Record::getModuleId, moduleId))
                .stream().map(record -> ModuleManageConverter.fromRecord(record, loadRecordValues(record.getId()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO createRecord(ModuleRecordSaveBO bo) {
        requireId(bo.getModuleId(), "moduleId");
        Model model = requireModel(bo.getModuleId());
        Record record = new Record();
        record.setTenantId(model.getTenantId());
        record.setSystemId(model.getSystemId());
        record.setAppId(model.getAppId());
        record.setModuleId(model.getId());
        record.setRecordNo(StrUtil.blankToDefault(bo.getRecordNo(), "REC-" + UUID.randomUUID()));
        record.setOwnerAccountId(ObjectUtil.defaultIfNull(bo.getOwnerAccountId(), currentAccountId()));
        record.setDeptId(bo.getDeptId());
        record.setRecordStatus(ACTIVE);
        record.setVersionNo(1);
        fillAudit(record::setCreatedBy, record::setUpdatedBy);
        recordService.save(record);
        saveRecordValues(record, bo.getValues());
        return ModuleManageConverter.fromRecord(record, loadRecordValues(record.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO updateRecord(Long id, ModuleRecordSaveBO bo) {
        Record record = requireRecord(id);
        updateRecordNoIfPresent(record, bo);
        record.setOwnerAccountId(ObjectUtil.defaultIfNull(bo.getOwnerAccountId(), record.getOwnerAccountId()));
        record.setDeptId(ObjectUtil.defaultIfNull(bo.getDeptId(), record.getDeptId()));
        record.setVersionNo(ObjectUtil.defaultIfNull(record.getVersionNo(), 1) + 1);
        fillUpdatedBy(record::setUpdatedBy);
        recordService.updateById(record);
        recordValueService.remove(Wrappers.<RecordValue>lambdaQuery().eq(RecordValue::getRecordId, record.getId()));
        saveRecordValues(record, bo.getValues());
        return ModuleManageConverter.fromRecord(record, loadRecordValues(record.getId()));
    }

    /**
     * 编辑记录编号。
     *
     * @param record 运行态记录
     * @param bo 更新入参
     */
    private void updateRecordNoIfPresent(Record record, ModuleRecordSaveBO bo) {
        if (ObjectUtil.isNotNull(bo.getModuleId()) && !bo.getModuleId().equals(record.getModuleId())) {
            throwError(ModuleManageErrorCode.FIELD_INVALID);
        }
        if (ObjectUtil.isNull(bo.getRecordNo())) {
            return;
        }
        String recordNo = StrUtil.trim(bo.getRecordNo());
        if (StrUtil.isBlank(recordNo) || recordNo.length() > RECORD_NO_MAX_LENGTH) {
            throwError(ModuleManageErrorCode.FIELD_INVALID);
        }
        long duplicateCount = recordService.count(Wrappers.<Record>lambdaQuery()
                .eq(Record::getTenantId, record.getTenantId())
                .eq(Record::getSystemId, record.getSystemId())
                .eq(Record::getModuleId, record.getModuleId())
                .eq(Record::getRecordNo, recordNo)
                .ne(Record::getId, record.getId()));
        if (duplicateCount > 0) {
            throwError(ModuleManageErrorCode.RECORD_NO_DUPLICATE);
        }
        record.setRecordNo(recordNo);
    }

    @Override
    public ModuleManageVO getRecord(Long id) {
        Record record = requireRecord(id);
        return ModuleManageConverter.fromRecord(record, loadRecordValues(record.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO deleteRecord(Long id) {
        Record record = requireRecord(id);
        recordService.removeById(id);
        recordValueService.remove(Wrappers.<RecordValue>lambdaQuery().eq(RecordValue::getRecordId, id));
        return ModuleManageConverter.fromRecord(record, loadRecordValues(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleManageVO createExportJob(ModuleExportJobBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getModuleId(), "moduleId");
        ExportJob job = new ExportJob();
        job.setTenantId(bo.getTenantId());
        job.setModuleId(bo.getModuleId());
        job.setJobType("EXPORT");
        job.setStatus(PENDING);
        job.setRequestJson(bo.getRequestJson());
        fillAudit(job::setCreatedBy, job::setUpdatedBy);
        exportJobService.save(job);
        return ModuleManageConverter.fromExportJob(job);
    }

    /**
     * 保存字段选项。
     *
     * @param fieldId 字段 ID
     * @param options 选项列表
     */
    private void saveFieldOptions(Long fieldId, List<ModuleFieldOptionDTO> options) {
        List<FieldOption> entities = options.stream().map(option -> {
            FieldOption entity = new FieldOption();
            entity.setFieldId(fieldId);
            entity.setOptionValue(option.getOptionValue());
            entity.setOptionLabel(option.getOptionLabel());
            entity.setSortOrder(ObjectUtil.defaultIfNull(option.getSortOrder(), 0));
            entity.setStatus("ENABLED");
            fillAudit(entity::setCreatedBy, entity::setUpdatedBy);
            return entity;
        }).toList();
        fieldOptionService.saveBatch(entities);
    }

    /**
     * 保存记录字段值。
     *
     * @param record 记录实体
     * @param values 字段值
     */
    private void saveRecordValues(Record record, Map<String, Object> values) {
        List<Field> fields = fieldService.list(Wrappers.<Field>lambdaQuery()
                .eq(Field::getModuleId, record.getModuleId()));
        if (CollUtil.isEmpty(fields)) {
            return;
        }
        Map<String, Object> safeValues = ObjectUtil.defaultIfNull(values, Map.of());
        List<RecordValue> entities = fields.stream()
                .filter(field -> safeValues.containsKey(field.getFieldCode()) || StrUtil.isNotBlank(field.getDefaultValue()))
                .map(field -> buildRecordValue(record, field, safeValues.get(field.getFieldCode())))
                .toList();
        if (CollUtil.isNotEmpty(entities)) {
            recordValueService.saveBatch(entities);
        }
    }

    /**
     * 构建字段值实体。
     *
     * @param record 记录实体
     * @param field 字段实体
     * @param rawValue 原始值
     * @return 字段值实体
     */
    private RecordValue buildRecordValue(Record record, Field field, Object rawValue) {
        Object value = ObjectUtil.defaultIfNull(rawValue, field.getDefaultValue());
        if (YES == ObjectUtil.defaultIfNull(field.getRequiredFlag(), (byte) 0) && ObjectUtil.isNull(value)) {
            throw new BusinessException(ModuleManageErrorCode.FIELD_INVALID.getCode(), field.getFieldCode() + " is required");
        }
        RecordValue entity = new RecordValue();
        entity.setRecordId(record.getId());
        entity.setModuleId(record.getModuleId());
        entity.setFieldId(field.getId());
        entity.setFieldCode(field.getFieldCode());
        if ("NUMBER".equals(field.getFieldType()) || "DECIMAL".equals(field.getFieldType())) {
            entity.setValueNumber(new BigDecimal(String.valueOf(value)));
        } else if ("DATE".equals(field.getFieldType()) || "DATETIME".equals(field.getFieldType())) {
            entity.setValueDatetime(LocalDateTime.parse(String.valueOf(value)));
        } else if ("MULTI_SELECT".equals(field.getFieldType()) || "FILE".equals(field.getFieldType())) {
            entity.setValueJson(String.valueOf(value));
        } else {
            entity.setValueText(String.valueOf(value));
        }
        fillAudit(entity::setCreatedBy, entity::setUpdatedBy);
        return entity;
    }

    /**
     * 加载记录字段值。
     *
     * @param recordId 记录 ID
     * @return 字段值映射
     */
    private Map<String, Object> loadRecordValues(Long recordId) {
        Map<String, Object> values = new HashMap<>();
        recordValueService.list(Wrappers.<RecordValue>lambdaQuery().eq(RecordValue::getRecordId, recordId))
                .forEach(value -> values.put(value.getFieldCode(), firstPresent(value)));
        return values;
    }

    /**
     * 读取字段值中第一个非空 typed value。
     *
     * @param value 字段值实体
     * @return 字段值
     */
    private Object firstPresent(RecordValue value) {
        if (ObjectUtil.isNotNull(value.getValueText())) {
            return value.getValueText();
        }
        if (ObjectUtil.isNotNull(value.getValueNumber())) {
            return value.getValueNumber();
        }
        if (ObjectUtil.isNotNull(value.getValueDatetime())) {
            return value.getValueDatetime();
        }
        return value.getValueJson();
    }

    /**
     * 查询并校验模块存在。
     *
     * @param id 模块 ID
     * @return 模块实体
     */
    private Model requireModel(Long id) {
        Model model = modelService.getById(id);
        if (ObjectUtil.isNull(model)) {
            throwError(ModuleManageErrorCode.DATA_NOT_FOUND);
        }
        return model;
    }

    /**
     * 查询并校验记录存在。
     *
     * @param id 记录 ID
     * @return 记录实体
     */
    private Record requireRecord(Long id) {
        Record record = recordService.getById(id);
        if (ObjectUtil.isNull(record)) {
            throwError(ModuleManageErrorCode.DATA_NOT_FOUND);
        }
        return record;
    }

    /**
     * 校验文本必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireText(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ModuleManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验 ID 必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireId(Long value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(ModuleManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 写入创建与更新人。
     *
     * @param createdSetter 创建人写入器
     * @param updatedSetter 更新人写入器
     */
    private void fillAudit(java.util.function.Consumer<Long> createdSetter, java.util.function.Consumer<Long> updatedSetter) {
        Long accountId = currentAccountId();
        createdSetter.accept(accountId);
        updatedSetter.accept(accountId);
    }

    /**
     * 写入更新人。
     *
     * @param updatedSetter 更新人写入器
     */
    private void fillUpdatedBy(java.util.function.Consumer<Long> updatedSetter) {
        updatedSetter.accept(currentAccountId());
    }

    /**
     * 获取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private Long currentAccountId() {
        AuthContext context = AuthContextHolder.get();
        return ObjectUtil.isNull(context) ? null : context.getAccountId();
    }

    /**
     * 抛出模块异常。
     *
     * @param errorCode 错误码
     */
    private void throwError(ModuleManageErrorCode errorCode) {
        throw new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }
}
