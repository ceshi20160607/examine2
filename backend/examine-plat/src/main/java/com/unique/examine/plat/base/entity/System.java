package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@TableName("un_plat_system")
public class System implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_code")
    private String systemCode;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("status")
    private String status;

    @TableField("tenant_mode")
    private String tenantMode;

    @TableField("owner_account_id")
    private Long ownerAccountId;

    @TableField("permission_version")
    private Long permissionVersion;

    @TableField("initialized_at")
    private LocalDateTime initializedAt;

    @TableField("init_failure_code")
    private String initFailureCode;

    @TableField("init_failed_at")
    private LocalDateTime initFailedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("archived_at")
    private LocalDateTime archivedAt;

    @TableLogic
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("deleted_by")
    private Long deletedBy;

    @TableField("tombstone_reason")
    private String tombstoneReason;

    @Version
    @TableField("version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenantMode() {
        return tenantMode;
    }

    public void setTenantMode(String tenantMode) {
        this.tenantMode = tenantMode;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public LocalDateTime getInitializedAt() {
        return initializedAt;
    }

    public void setInitializedAt(LocalDateTime initializedAt) {
        this.initializedAt = initializedAt;
    }

    public String getInitFailureCode() {
        return initFailureCode;
    }

    public void setInitFailureCode(String initFailureCode) {
        this.initFailureCode = initFailureCode;
    }

    public LocalDateTime getInitFailedAt() {
        return initFailedAt;
    }

    public void setInitFailedAt(LocalDateTime initFailedAt) {
        this.initFailedAt = initFailedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getTombstoneReason() {
        return tombstoneReason;
    }

    public void setTombstoneReason(String tombstoneReason) {
        this.tombstoneReason = tombstoneReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
