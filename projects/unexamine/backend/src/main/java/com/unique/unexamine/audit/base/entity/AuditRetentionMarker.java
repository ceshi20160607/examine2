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
@TableName("audit_retention_marker")
public class AuditRetentionMarker {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_type")
    private String contextType;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("object_type")
    private String objectType;

    @TableField("object_id")
    private String objectId;

    @TableField("business_key")
    private String businessKey;

    @TableField("snapshot_hash")
    private String snapshotHash;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("purge_reason")
    private String purgeReason;

    @TableField("purged_by_account_id")
    private Long purgedByAccountId;

    @TableField("purged_at")
    private LocalDateTime purgedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getPurgeReason() {
        return purgeReason;
    }

    public void setPurgeReason(String purgeReason) {
        this.purgeReason = purgeReason;
    }

    public Long getPurgedByAccountId() {
        return purgedByAccountId;
    }

    public void setPurgedByAccountId(Long purgedByAccountId) {
        this.purgedByAccountId = purgedByAccountId;
    }

    public LocalDateTime getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(LocalDateTime purgedAt) {
        this.purgedAt = purgedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AuditRetentionMarker{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", objectType = " + objectType +
            ", objectId = " + objectId +
            ", businessKey = " + businessKey +
            ", snapshotHash = " + snapshotHash +
            ", snapshotJson = " + snapshotJson +
            ", purgeReason = " + purgeReason +
            ", purgedByAccountId = " + purgedByAccountId +
            ", purgedAt = " + purgedAt +
            ", createdAt = " + createdAt +
            "}";
    }
}
