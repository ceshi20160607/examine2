package com.unique.unexamine.audit.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变的业务字段变更前后值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_audit_field_change")
public class AuditFieldChange {

    /**
     * 字段变更审计主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对应的操作审计事件主键
     */
    @TableField("audit_event_id")
    private Long auditEventId;

    /**
     * 发生变更的业务字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段值的数据类型
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 变更前字段值，敏感信息须脱敏
     */
    @TableField("before_value_json")
    private String beforeValueJson;

    /**
     * 变更后字段值，敏感信息须脱敏
     */
    @TableField("after_value_json")
    private String afterValueJson;

    /**
     * 字段值敏感等级
     */
    @TableField("sensitivity")
    private String sensitivity;

    /**
     * 字段变更审计记录时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
            ", createTime = " + createTime +
            "}";
    }
}
