package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块字段
 * </p>
 *
 * @author Template Base
 * @since 2026-10-24
 */
@TableName("un_module_field")
public class UnModuleField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义字段的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 固定字段或自定义字段的英文键
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * 字段展示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 固定字段类型编号，按core统一枚举
     */
    @TableField("field_type")
    private Short fieldType;

    /**
     * 保存时是否必填
     */
    @TableField("required_flag")
    private Boolean requiredFlag;

    /**
     * 表单是否只读
     */
    @TableField("readonly_flag")
    private Boolean readonlyFlag;

    /**
     * 表单是否隐藏
     */
    @TableField("hidden_flag")
    private Boolean hiddenFlag;

    /**
     * 当前租户模块内是否要求值唯一
     */
    @TableField("unique_flag")
    private Boolean uniqueFlag;

    /**
     * 字段说明和输入提示
     */
    @TableField("hint_text")
    private String hintText;

    /**
     * 表单输入占位文案
     */
    @TableField("placeholder_text")
    private String placeholderText;

    /**
     * 按字段类型校验后的默认值文本
     */
    @TableField("default_value")
    private String defaultValue;

    /**
     * 按字段类型解释的表单及详情展示样式
     */
    @TableField("display_style")
    private String displayStyle;

    /**
     * 文本最少字符数
     */
    @TableField("min_length")
    private Integer minLength;

    /**
     * 文本最多字符数
     */
    @TableField("max_length")
    private Integer maxLength;

    /**
     * 文本格式校验正则
     */
    @TableField("input_pattern")
    private String inputPattern;

    /**
     * 数字允许的最小值
     */
    @TableField("number_min")
    private BigDecimal numberMin;

    /**
     * 数字允许的最大值
     */
    @TableField("number_max")
    private BigDecimal numberMax;

    /**
     * 数字总位数
     */
    @TableField("number_precision")
    private Byte numberPrecision;

    /**
     * 数字小数位数
     */
    @TableField("number_scale")
    private Byte numberScale;

    /**
     * 超出小数位时是否四舍五入
     */
    @TableField("rounding_flag")
    private Boolean roundingFlag;

    /**
     * 数字单位对应的字典编码
     */
    @TableField("unit_dict_code")
    private String unitDictCode;

    /**
     * 金额样式使用的ISO货币代码
     */
    @TableField("currency_code")
    private String currencyCode;

    /**
     * 多值字段最少选取项数
     */
    @TableField("min_items")
    private Integer minItems;

    /**
     * 多值字段最多选取项数
     */
    @TableField("max_items")
    private Integer maxItems;

    /**
     * 选项、人员、部门或关联模块是否可多选
     */
    @TableField("multiple_flag")
    private Boolean multipleFlag;

    /**
     * 选项或级联使用的字典编码
     */
    @TableField("dict_code")
    private String dictCode;

    /**
     * 字段结构化配置：选项联动、关联方式及目标字段、自动编号和公式规则
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 关联字段指向的已存在目标模块主键
     */
    @TableField("relation_module_id")
    private Long relationModuleId;

    /**
     * 设计器保存的字段顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 字段所在表单行号
     */
    @TableField("row_no")
    private Integer rowNo;

    /**
     * 字段所在行的列号
     */
    @TableField("column_no")
    private Integer columnNo;

    /**
     * 字段在所在行的占位宽度百分比
     */
    @TableField("width_percent")
    private Byte widthPercent;

    /**
     * 字段启停状态，保存即生效
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Short getFieldType() {
        return fieldType;
    }

    public void setFieldType(Short fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public Boolean getReadonlyFlag() {
        return readonlyFlag;
    }

    public void setReadonlyFlag(Boolean readonlyFlag) {
        this.readonlyFlag = readonlyFlag;
    }

    public Boolean getHiddenFlag() {
        return hiddenFlag;
    }

    public void setHiddenFlag(Boolean hiddenFlag) {
        this.hiddenFlag = hiddenFlag;
    }

    public Boolean getUniqueFlag() {
        return uniqueFlag;
    }

    public void setUniqueFlag(Boolean uniqueFlag) {
        this.uniqueFlag = uniqueFlag;
    }

    public String getHintText() {
        return hintText;
    }

    public void setHintText(String hintText) {
        this.hintText = hintText;
    }

    public String getPlaceholderText() {
        return placeholderText;
    }

    public void setPlaceholderText(String placeholderText) {
        this.placeholderText = placeholderText;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDisplayStyle() {
        return displayStyle;
    }

    public void setDisplayStyle(String displayStyle) {
        this.displayStyle = displayStyle;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getInputPattern() {
        return inputPattern;
    }

    public void setInputPattern(String inputPattern) {
        this.inputPattern = inputPattern;
    }

    public BigDecimal getNumberMin() {
        return numberMin;
    }

    public void setNumberMin(BigDecimal numberMin) {
        this.numberMin = numberMin;
    }

    public BigDecimal getNumberMax() {
        return numberMax;
    }

    public void setNumberMax(BigDecimal numberMax) {
        this.numberMax = numberMax;
    }

    public Byte getNumberPrecision() {
        return numberPrecision;
    }

    public void setNumberPrecision(Byte numberPrecision) {
        this.numberPrecision = numberPrecision;
    }

    public Byte getNumberScale() {
        return numberScale;
    }

    public void setNumberScale(Byte numberScale) {
        this.numberScale = numberScale;
    }

    public Boolean getRoundingFlag() {
        return roundingFlag;
    }

    public void setRoundingFlag(Boolean roundingFlag) {
        this.roundingFlag = roundingFlag;
    }

    public String getUnitDictCode() {
        return unitDictCode;
    }

    public void setUnitDictCode(String unitDictCode) {
        this.unitDictCode = unitDictCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public void setMinItems(Integer minItems) {
        this.minItems = minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
    }

    public Boolean getMultipleFlag() {
        return multipleFlag;
    }

    public void setMultipleFlag(Boolean multipleFlag) {
        this.multipleFlag = multipleFlag;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Long getRelationModuleId() {
        return relationModuleId;
    }

    public void setRelationModuleId(Long relationModuleId) {
        this.relationModuleId = relationModuleId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public Integer getColumnNo() {
        return columnNo;
    }

    public void setColumnNo(Integer columnNo) {
        this.columnNo = columnNo;
    }

    public Byte getWidthPercent() {
        return widthPercent;
    }

    public void setWidthPercent(Byte widthPercent) {
        this.widthPercent = widthPercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
