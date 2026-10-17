package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块字段草稿定义
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module_field")
public class ConfiguredModuleField {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 所属模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 范围内稳定编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 字段业务类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 是否必填
     */
    @TableField("required")
    private Boolean required;

    /**
     * 是否要求业务值唯一
     */
    @TableField("unique_value")
    private Boolean uniqueValue;

    /**
     * 是否进入搜索范围
     */
    @TableField("searchable")
    private Boolean searchable;

    /**
     * 所属字典标识
     */
    @TableField("dictionary_id")
    private Long dictionaryId;

    /**
     * 关联数据来源模块标识
     */
    @TableField("reference_module_id")
    private Long referenceModuleId;

    /**
     * 所属子表字段标识，空值表示主记录字段
     */
    @TableField("parent_field_id")
    private Long parentFieldId;

    /**
     * 显示排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 字段或动作的类型专属配置
     */
    @TableField("config_json")
    private String configJson;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Boolean getUniqueValue() {
        return uniqueValue;
    }

    public void setUniqueValue(Boolean uniqueValue) {
        this.uniqueValue = uniqueValue;
    }

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public Long getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(Long dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public Long getReferenceModuleId() {
        return referenceModuleId;
    }

    public void setReferenceModuleId(Long referenceModuleId) {
        this.referenceModuleId = referenceModuleId;
    }

    public Long getParentFieldId() {
        return parentFieldId;
    }

    public void setParentFieldId(Long parentFieldId) {
        this.parentFieldId = parentFieldId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
        return "ConfiguredModuleField{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", fieldType = " + fieldType +
            ", required = " + required +
            ", uniqueValue = " + uniqueValue +
            ", searchable = " + searchable +
            ", dictionaryId = " + dictionaryId +
            ", referenceModuleId = " + referenceModuleId +
            ", parentFieldId = " + parentFieldId +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", configJson = " + configJson +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
