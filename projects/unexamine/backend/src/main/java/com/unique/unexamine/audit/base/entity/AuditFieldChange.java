package com.unique.unexamine.audit.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("audit_field_change")
public class AuditFieldChange {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("audit_event_id")
    private Long auditEventId;

    @TableField("field_code")
    private String fieldCode;

    @TableField("value_type")
    private String valueType;

    @TableField("before_value_json")
    private String beforeValueJson;

    @TableField("after_value_json")
    private String afterValueJson;

    @TableField("sensitivity")
    private String sensitivity;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(Long auditEventId) {
        this.auditEventId = auditEventId;
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

    public String getBeforeValueJson() {
        return beforeValueJson;
    }

    public void setBeforeValueJson(String beforeValueJson) {
        this.beforeValueJson = beforeValueJson;
    }

    public String getAfterValueJson() {
        return afterValueJson;
    }

    public void setAfterValueJson(String afterValueJson) {
        this.afterValueJson = afterValueJson;
    }

    public String getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(String sensitivity) {
        this.sensitivity = sensitivity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AuditFieldChange{" +
            "id = " + id +
            ", auditEventId = " + auditEventId +
            ", fieldCode = " + fieldCode +
            ", valueType = " + valueType +
            ", beforeValueJson = " + beforeValueJson +
            ", afterValueJson = " + afterValueJson +
            ", sensitivity = " + sensitivity +
            ", createdAt = " + createdAt +
            "}";
    }
}
