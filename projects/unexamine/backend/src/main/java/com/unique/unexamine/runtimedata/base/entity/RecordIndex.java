package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 可由字段原值重建的类型化查询索引
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_index")
public class RecordIndex {

    /**
     * 记录查询索引标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 记录原始所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属业务模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 关联通用业务主记录标识
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 索引对应的发布字段标识
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 文本型筛选和排序值
     */
    @TableField("value_text")
    private String valueText;

    /**
     * 数值型筛选和排序值
     */
    @TableField("value_number")
    private BigDecimal valueNumber;

    /**
     * 日期时间型筛选和排序值
     */
    @TableField("value_time")
    private LocalDateTime valueTime;

    /**
     * 布尔型筛选值
     */
    @TableField("value_boolean")
    private Boolean valueBoolean;

    /**
     * 索引值最近重建时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", valueText = " + valueText +
            ", valueNumber = " + valueNumber +
            ", valueTime = " + valueTime +
            ", valueBoolean = " + valueBoolean +
            ", updateTime = " + updateTime +
            "}";
    }
}
