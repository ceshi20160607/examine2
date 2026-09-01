package com.unique.unexamine.runtimedata.base.entity;

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
@TableName("biz_record")
public class BusinessRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("created_config_version_id")
    private Long createdConfigVersionId;

    @TableField("updated_config_version_id")
    private Long updatedConfigVersionId;

    @TableField("record_number")
    private String recordNumber;

    @TableField("title")
    private String title;

    @TableField("`status`")
    private String status;

    @TableField("owner_member_id")
    private Long ownerMemberId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    @TableField("archived")
    private Boolean archived;

    @TableField("archived_at")
    private LocalDateTime archivedAt;

    @TableField("deleted")
    private Boolean deleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

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

    public Long getCreatedConfigVersionId() {
        return createdConfigVersionId;
    }

    public void setCreatedConfigVersionId(Long createdConfigVersionId) {
        this.createdConfigVersionId = createdConfigVersionId;
    }

    public Long getUpdatedConfigVersionId() {
        return updatedConfigVersionId;
    }

    public void setUpdatedConfigVersionId(Long updatedConfigVersionId) {
        this.updatedConfigVersionId = updatedConfigVersionId;
    }

    public String getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(String recordNumber) {
        this.recordNumber = recordNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
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
        return "BusinessRecord{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", createdConfigVersionId = " + createdConfigVersionId +
            ", updatedConfigVersionId = " + updatedConfigVersionId +
            ", recordNumber = " + recordNumber +
            ", title = " + title +
            ", status = " + status +
            ", ownerMemberId = " + ownerMemberId +
            ", departmentId = " + departmentId +
            ", createdByMemberId = " + createdByMemberId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", archived = " + archived +
            ", archivedAt = " + archivedAt +
            ", deleted = " + deleted +
            ", deletedAt = " + deletedAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
