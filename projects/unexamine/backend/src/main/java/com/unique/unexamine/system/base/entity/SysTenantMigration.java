package com.unique.unexamine.system.base.entity;

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
@TableName("sys_tenant_migration")
public class SysTenantMigration {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("from_mode")
    private String fromMode;

    @TableField("to_mode")
    private String toMode;

    @TableField("impact_snapshot_json")
    private String impactSnapshotJson;

    @TableField("`status`")
    private String status;

    @TableField("job_id")
    private Long jobId;

    @TableField("requested_by_member_id")
    private Long requestedByMemberId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("rollback_snapshot_json")
    private String rollbackSnapshotJson;

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

    public String getFromMode() {
        return fromMode;
    }

    public void setFromMode(String fromMode) {
        this.fromMode = fromMode;
    }

    public String getToMode() {
        return toMode;
    }

    public void setToMode(String toMode) {
        this.toMode = toMode;
    }

    public String getImpactSnapshotJson() {
        return impactSnapshotJson;
    }

    public void setImpactSnapshotJson(String impactSnapshotJson) {
        this.impactSnapshotJson = impactSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getRequestedByMemberId() {
        return requestedByMemberId;
    }

    public void setRequestedByMemberId(Long requestedByMemberId) {
        this.requestedByMemberId = requestedByMemberId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getRollbackSnapshotJson() {
        return rollbackSnapshotJson;
    }

    public void setRollbackSnapshotJson(String rollbackSnapshotJson) {
        this.rollbackSnapshotJson = rollbackSnapshotJson;
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
        return "SysTenantMigration{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", fromMode = " + fromMode +
            ", toMode = " + toMode +
            ", impactSnapshotJson = " + impactSnapshotJson +
            ", status = " + status +
            ", jobId = " + jobId +
            ", requestedByMemberId = " + requestedByMemberId +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", rollbackSnapshotJson = " + rollbackSnapshotJson +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
