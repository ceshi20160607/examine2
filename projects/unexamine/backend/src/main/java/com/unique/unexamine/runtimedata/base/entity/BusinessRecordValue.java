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
 * 业务主记录的自定义字段值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_value")
public class BusinessRecordValue {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 模块字段配置主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 写入时字段的稳定业务编码
     */
    @TableField("field_code")
    private String fieldCode;

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
     * 关联业务记录主键
     */
    @TableField("value_reference_id")
    private Long valueReferenceId;

    /**
     * 附件文件主键
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
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
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

    @Override
    public String toString() {
        return "BusinessRecordValue{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", fieldCode = " + fieldCode +
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
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
