package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 通用子表明细行的独立列字段值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_child_value")
public class BizRecordChildValue {

    /**
     * 子表行字段值主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 子表所属模块
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 子表定义字段主键
     */
    @TableField("child_field_id")
    private Long childFieldId;

    /**
     * 所属子表行主键
     */
    @TableField("child_row_id")
    private Long childRowId;

    /**
     * 子表列字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 字段值存储类型
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 文本类型原始值
     */
    @TableField("value_text")
    private String valueText;

    /**
     * 数值类型原始值
     */
    @TableField("value_number")
    private BigDecimal valueNumber;

    /**
     * 日期类型原始值
     */
    @TableField("value_date")
    private LocalDate valueDate;

    /**
     * 日期时间类型原始值
     */
    @TableField("value_time")
    private LocalDateTime valueTime;

    /**
     * 布尔类型原始值
     */
    @TableField("value_boolean")
    private Boolean valueBoolean;

    /**
     * 关联记录主键，读取时继续校验目标模块和权限
     */
    @TableField("value_reference_id")
    private Long valueReferenceId;

    /**
     * 附件文件主键，读取时继续校验访问权限
     */
    @TableField("value_file_id")
    private Long valueFileId;

    /**
     * 复杂类型原始值
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 用于检索的标准化文本
     */
    @TableField("normalized_text")
    private String normalizedText;

    /**
     * 值创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 值最近修改时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getChildFieldId() {
        return childFieldId;
    }

    public void setChildFieldId(Long childFieldId) {
        this.childFieldId = childFieldId;
    }

    public Long getChildRowId() {
        return childRowId;
    }

    public void setChildRowId(Long childRowId) {
        this.childRowId = childRowId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(BigDecimal valueNumber) {
        this.valueNumber = valueNumber;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public LocalDateTime getValueTime() {
        return valueTime;
    }

    public void setValueTime(LocalDateTime valueTime) {
        this.valueTime = valueTime;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Boolean valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public Long getValueReferenceId() {
        return valueReferenceId;
    }

    public void setValueReferenceId(Long valueReferenceId) {
        this.valueReferenceId = valueReferenceId;
    }

    public Long getValueFileId() {
        return valueFileId;
    }

    public void setValueFileId(Long valueFileId) {
        this.valueFileId = valueFileId;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "BizRecordChildValue{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", childFieldId = " + childFieldId +
            ", childRowId = " + childRowId +
            ", fieldId = " + fieldId +
            ", valueType = " + valueType +
            ", valueText = " + valueText +
            ", valueNumber = " + valueNumber +
            ", valueDate = " + valueDate +
            ", valueTime = " + valueTime +
            ", valueBoolean = " + valueBoolean +
            ", valueReferenceId = " + valueReferenceId +
            ", valueFileId = " + valueFileId +
            ", valueJson = " + valueJson +
            ", normalizedText = " + normalizedText +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            "}";
    }
}
