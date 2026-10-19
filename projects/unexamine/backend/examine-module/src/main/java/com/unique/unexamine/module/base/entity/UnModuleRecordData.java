package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块通用业务扩展记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_record_data")
public class UnModuleRecordData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块业务字段值主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 所属业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 基础或租户扩展字段的稳定编码
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 值的存储类型：text、number、time 或 json
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 文本值，文本排序按完整内容比较
     */
    @TableField("value_text")
    private String valueText;

    /**
     * 数值字段的可比较数值
     */
    @TableField("value_number")
    private BigDecimal valueNumber;

    /**
     * 日期时间字段的可比较时间
     */
    @TableField("value_time")
    private LocalDateTime valueTime;

    /**
     * 多选、文件等集合结构的值
     */
    @TableField("value_json")
    private String valueJson;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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

    public LocalDateTime getValueTime() {
        return valueTime;
    }

    public void setValueTime(LocalDateTime valueTime) {
        this.valueTime = valueTime;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
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
}
