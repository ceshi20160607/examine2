package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块字段草稿配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module_field")
public class ModuleField {

    /**
     * 模块字段标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 字段配置来源租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内稳定字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段显示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 已支持的字段类型编码
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 表单是否必填：1是，0否
     */
    @TableField("required_flag")
    private Boolean requiredFlag;

    /**
     * 是否允许列表筛选：1是，0否
     */
    @TableField("searchable_flag")
    private Boolean searchableFlag;

    /**
     * 默认表单和列表排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 字段类型相关约束与显示配置的 JSON 文本
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 字段草稿状态：1启用，0停用
     */
    @TableField("`status`")
    private Boolean status;

    /**
     * 字段创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 字段修改时间
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

    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public Boolean getSearchableFlag() {
        return searchableFlag;
    }

    public void setSearchableFlag(Boolean searchableFlag) {
        this.searchableFlag = searchableFlag;
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

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
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

    @Override
    public String toString() {
        return "ModuleField{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", fieldCode = " + fieldCode +
            ", fieldName = " + fieldName +
            ", fieldType = " + fieldType +
            ", requiredFlag = " + requiredFlag +
            ", searchableFlag = " + searchableFlag +
            ", sortOrder = " + sortOrder +
            ", configJson = " + configJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
