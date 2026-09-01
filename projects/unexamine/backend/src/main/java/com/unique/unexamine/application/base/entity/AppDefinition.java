package com.unique.unexamine.application.base.entity;

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
@TableName("app_definition")
public class AppDefinition {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_type")
    private String contextType;

    @TableField("platform_id")
    private Long platformId;

    @TableField("owner_system_id")
    private Long ownerSystemId;

    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("`description`")
    private String description;

    @TableField("application_type")
    private String applicationType;

    @TableField("draft_revision")
    private Integer draftRevision;

    @TableField("`status`")
    private String status;

    @TableField("created_by_account_id")
    private Long createdByAccountId;

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

    public Long getOwnerSystemId() {
        return ownerSystemId;
    }

    public void setOwnerSystemId(Long ownerSystemId) {
        this.ownerSystemId = ownerSystemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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
        return "AppDefinition{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", ownerSystemId = " + ownerSystemId +
            ", ownerTenantId = " + ownerTenantId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", applicationType = " + applicationType +
            ", draftRevision = " + draftRevision +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
