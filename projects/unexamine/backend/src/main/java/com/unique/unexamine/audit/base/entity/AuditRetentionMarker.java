package com.unique.unexamine.audit.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务数据清理后的不可变留痕
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_audit_retention_marker")
public class AuditRetentionMarker {

    /**
     * 清理留痕记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被清理对象所属的平台或系统范围类型
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 被清理对象所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 被清理对象所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 被清理业务对象类型
     */
    @TableField("object_type")
    private String objectType;

    /**
     * 被清理业务对象标识
     */
    @TableField("object_id")
    private String objectId;

    /**
     * 便于后续追溯的业务编号
     */
    @TableField("business_key")
    private String businessKey;

    /**
     * 清理前业务快照的摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 按留存策略保留的清理前业务快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 清理原因及业务依据
     */
    @TableField("purge_reason")
    private String purgeReason;

    /**
     * 执行清理的平台账号主键
     */
    @TableField("purged_by_account_id")
    private Long purgedByAccountId;

    /**
     * 实际清理业务数据的时间
     */
    @TableField("purge_time")
    private LocalDateTime purgeTime;

    /**
     * 清理留痕记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public LocalDateTime getPurgeTime() {
        return purgeTime;
    }

    public void setPurgeTime(LocalDateTime purgeTime) {
        this.purgeTime = purgeTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
            ", purgeTime = " + purgeTime +
            ", createTime = " + createTime +
            "}";
    }
}
