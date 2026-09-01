package com.unique.unexamine.operations.base.entity;

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
@TableName("ops_upgrade")
public class OpsUpgrade {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("release_id")
    private Long releaseId;

    @TableField("backup_id")
    private Long backupId;

    @TableField("context_type")
    private String contextType;

    @TableField("system_id")
    private Long systemId;

    @TableField("impact_report_json")
    private String impactReportJson;

    @TableField("`status`")
    private String status;

    @TableField("rollback_point_json")
    private String rollbackPointJson;

    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public Long getBackupId() {
        return backupId;
    }

    public void setBackupId(Long backupId) {
        this.backupId = backupId;
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

    public String getImpactReportJson() {
        return impactReportJson;
    }

    public void setImpactReportJson(String impactReportJson) {
        this.impactReportJson = impactReportJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRollbackPointJson() {
        return rollbackPointJson;
    }

    public void setRollbackPointJson(String rollbackPointJson) {
        this.rollbackPointJson = rollbackPointJson;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsUpgrade{" +
            "id = " + id +
            ", releaseId = " + releaseId +
            ", backupId = " + backupId +
            ", contextType = " + contextType +
            ", systemId = " + systemId +
            ", impactReportJson = " + impactReportJson +
            ", status = " + status +
            ", rollbackPointJson = " + rollbackPointJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", createdAt = " + createdAt +
            ", version = " + version +
            "}";
    }
}
