package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 通用业务记录的动态字段原值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_data")
public class RecordData {

    /**
     * 记录字段原值标识
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
     * 发布字段标识
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 写入时字段稳定编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段原值的 JSON 文本，按发布类型校验
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 字段值最近修改时间
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

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "RecordData{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", fieldCode = " + fieldCode +
            ", valueJson = " + valueJson +
            ", updateTime = " + updateTime +
            "}";
    }
}
