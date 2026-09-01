package com.unique.unexamine.moduleconfig.base.entity;

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
@TableName("cfg_module")
public class ConfiguredModule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    @TableField("group_id")
    private Long groupId;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("`description`")
    private String description;

    @TableField("title_field_code")
    private String titleFieldCode;

    @TableField("number_sequence_code")
    private String numberSequenceCode;

    @TableField("status_field_code")
    private String statusFieldCode;

    @TableField("`status`")
    private String status;

    @TableField("draft_revision")
    private Integer draftRevision;

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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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

    public String getTitleFieldCode() {
        return titleFieldCode;
    }

    public void setTitleFieldCode(String titleFieldCode) {
        this.titleFieldCode = titleFieldCode;
    }

    public String getNumberSequenceCode() {
        return numberSequenceCode;
    }

    public void setNumberSequenceCode(String numberSequenceCode) {
        this.numberSequenceCode = numberSequenceCode;
    }

    public String getStatusFieldCode() {
        return statusFieldCode;
    }

    public void setStatusFieldCode(String statusFieldCode) {
        this.statusFieldCode = statusFieldCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
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
        return "ConfiguredModule{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", groupId = " + groupId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", titleFieldCode = " + titleFieldCode +
            ", numberSequenceCode = " + numberSequenceCode +
            ", statusFieldCode = " + statusFieldCode +
            ", status = " + status +
            ", draftRevision = " + draftRevision +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
