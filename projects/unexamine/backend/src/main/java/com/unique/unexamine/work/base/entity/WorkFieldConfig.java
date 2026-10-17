package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 工作域的可配置字段
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_field_config")
public class WorkFieldConfig {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务上下文类型：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统标识，平台范围为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识，平台范围为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 可配置字段所属对象类型
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 同一对象范围内的字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段显示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 字段数据类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 是否必填
     */
    @TableField("required")
    private Boolean required;

    /**
     * 组内排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 字段展示与校验配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最后修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "WorkFieldConfig{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", targetType = " + targetType +
            ", fieldCode = " + fieldCode +
            ", fieldName = " + fieldName +
            ", fieldType = " + fieldType +
            ", required = " + required +
            ", sortOrder = " + sortOrder +
            ", configJson = " + configJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
