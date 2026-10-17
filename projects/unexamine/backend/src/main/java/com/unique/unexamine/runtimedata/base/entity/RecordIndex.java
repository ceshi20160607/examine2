package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 可筛选字段的类型化查询索引值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_record_index")
public class RecordIndex {

    /**
     * 记录查询索引主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 可筛选的模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 用于精确和前缀筛选的文本值
     */
    @TableField("text_value")
    private String textValue;

    /**
     * 用于范围筛选的数字值
     */
    @TableField("number_value")
    private BigDecimal numberValue;

    /**
     * 用于时间范围筛选的日期值
     */
    @TableField("date_value")
    private LocalDateTime dateValue;

    /**
     * 用于布尔筛选的字段值
     */
    @TableField("boolean_value")
    private Boolean booleanValue;

    /**
     * 查询索引值更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public BigDecimal getNumberValue() {
        return numberValue;
    }

    public void setNumberValue(BigDecimal numberValue) {
        this.numberValue = numberValue;
    }

    public LocalDateTime getDateValue() {
        return dateValue;
    }

    public void setDateValue(LocalDateTime dateValue) {
        this.dateValue = dateValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "RecordIndex{" +
            "id = " + id +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", textValue = " + textValue +
            ", numberValue = " + numberValue +
            ", dateValue = " + dateValue +
            ", booleanValue = " + booleanValue +
            ", updateTime = " + updateTime +
            "}";
    }
}
