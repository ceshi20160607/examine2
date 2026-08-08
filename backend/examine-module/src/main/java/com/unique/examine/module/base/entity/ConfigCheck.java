package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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
@TableName("un_module_config_check")
public class ConfigCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("base_version_id")
    private Long baseVersionId;

    @TableField("draft_revision")
    private Long draftRevision;

    @TableField("draft_checksum")
    private String draftChecksum;

    @TableField("status")
    private String status;

    @TableField("blocker_count")
    private Integer blockerCount;

    @TableField("warning_count")
    private Integer warningCount;

    @TableField("snapshot_size_bytes")
    private Long snapshotSizeBytes;

    @TableField("report_json")
    private String reportJson;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("checked_by")
    private Long checkedBy;

    @Version
    @TableField("version")
    private Long version;

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

    public Long getBaseVersionId() {
        return baseVersionId;
    }

    public void setBaseVersionId(Long baseVersionId) {
        this.baseVersionId = baseVersionId;
    }

    public Long getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Long draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getDraftChecksum() {
        return draftChecksum;
    }

    public void setDraftChecksum(String draftChecksum) {
        this.draftChecksum = draftChecksum;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBlockerCount() {
        return blockerCount;
    }

    public void setBlockerCount(Integer blockerCount) {
        this.blockerCount = blockerCount;
    }

    public Integer getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(Integer warningCount) {
        this.warningCount = warningCount;
    }

    public Long getSnapshotSizeBytes() {
        return snapshotSizeBytes;
    }

    public void setSnapshotSizeBytes(Long snapshotSizeBytes) {
        this.snapshotSizeBytes = snapshotSizeBytes;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getCheckedBy() {
        return checkedBy;
    }

    public void setCheckedBy(Long checkedBy) {
        this.checkedBy = checkedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
