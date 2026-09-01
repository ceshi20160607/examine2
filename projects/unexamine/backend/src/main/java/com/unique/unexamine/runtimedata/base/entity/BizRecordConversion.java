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
@TableName("biz_record_conversion")
public class BizRecordConversion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("source_record_id")
    private Long sourceRecordId;

    @TableField("action_id")
    private Long actionId;

    @TableField("`status`")
    private String status;

    @TableField("mapping_snapshot_json")
    private String mappingSnapshotJson;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

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

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getActionId() {
        return actionId;
    }

    public void setActionId(Long actionId) {
        this.actionId = actionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMappingSnapshotJson() {
        return mappingSnapshotJson;
    }

    public void setMappingSnapshotJson(String mappingSnapshotJson) {
        this.mappingSnapshotJson = mappingSnapshotJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BizRecordConversion{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", sourceRecordId = " + sourceRecordId +
            ", actionId = " + actionId +
            ", status = " + status +
            ", mappingSnapshotJson = " + mappingSnapshotJson +
            ", idempotencyKey = " + idempotencyKey +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", finishedAt = " + finishedAt +
            ", version = " + version +
            "}";
    }
}
