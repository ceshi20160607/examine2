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
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_record_value")
public class BusinessRecordValue {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("record_id")
    private Long recordId;

    @TableField("field_id")
    private Long fieldId;

    @TableField("field_code")
    private String fieldCode;

    @TableField("value_type")
    private String valueType;

    @TableField("value_text")
    private String valueText;

    @TableField("value_number")
    private BigDecimal valueNumber;

    @TableField("value_date")
    private LocalDate valueDate;

    @TableField("value_datetime")
    private LocalDateTime valueDatetime;

    @TableField("value_boolean")
    private Boolean valueBoolean;

    @TableField("value_reference_id")
    private Long valueReferenceId;

    @TableField("value_json")
    private String valueJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getValueDatetime() {
        return valueDatetime;
    }

    public void setValueDatetime(LocalDateTime valueDatetime) {
        this.valueDatetime = valueDatetime;
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

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "BusinessRecordValue{" +
            "id = " + id +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", fieldCode = " + fieldCode +
            ", valueType = " + valueType +
            ", valueText = " + valueText +
            ", valueNumber = " + valueNumber +
            ", valueDate = " + valueDate +
            ", valueDatetime = " + valueDatetime +
            ", valueBoolean = " + valueBoolean +
            ", valueReferenceId = " + valueReferenceId +
            ", valueJson = " + valueJson +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            "}";
    }
}
