package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("exchange_mapping")
public class ExchangeMapping {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("mapping_type")
    private String mappingType;

    @TableField("column_mapping_json")
    private String columnMappingJson;

    @TableField("validation_policy_json")
    private String validationPolicyJson;

    @TableField("conflict_policy_json")
    private String conflictPolicyJson;

    @TableField("`status`")
    private String status;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public String getMappingType() {
        return mappingType;
    }

    public void setMappingType(String mappingType) {
        this.mappingType = mappingType;
    }

    public String getColumnMappingJson() {
        return columnMappingJson;
    }

    public void setColumnMappingJson(String columnMappingJson) {
        this.columnMappingJson = columnMappingJson;
    }

    public String getValidationPolicyJson() {
        return validationPolicyJson;
    }

    public void setValidationPolicyJson(String validationPolicyJson) {
        this.validationPolicyJson = validationPolicyJson;
    }

    public String getConflictPolicyJson() {
        return conflictPolicyJson;
    }

    public void setConflictPolicyJson(String conflictPolicyJson) {
        this.conflictPolicyJson = conflictPolicyJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ExchangeMapping{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", mappingType = " + mappingType +
            ", columnMappingJson = " + columnMappingJson +
            ", validationPolicyJson = " + validationPolicyJson +
            ", conflictPolicyJson = " + conflictPolicyJson +
            ", status = " + status +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
