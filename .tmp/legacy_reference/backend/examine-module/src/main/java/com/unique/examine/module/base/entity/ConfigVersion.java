package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
@TableName("un_module_config_version")
public class ConfigVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("version_no")
    private Long versionNo;

    @TableField("source_type")
    private String sourceType;

    @TableField("based_on_version_id")
    private Long basedOnVersionId;

    @TableField("rollback_target_version_id")
    private Long rollbackTargetVersionId;

    @TableField("source_check_id")
    private Long sourceCheckId;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("snapshot_checksum")
    private String snapshotChecksum;

    @TableField("snapshot_size_bytes")
    private Long snapshotSizeBytes;

    @TableField("impact_report_json")
    private String impactReportJson;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("published_by")
    private Long publishedBy;

    @TableField("publish_reason")
    private String publishReason;

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

    public Long getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Long versionNo) {
        this.versionNo = versionNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getBasedOnVersionId() {
        return basedOnVersionId;
    }

    public void setBasedOnVersionId(Long basedOnVersionId) {
        this.basedOnVersionId = basedOnVersionId;
    }

    public Long getRollbackTargetVersionId() {
        return rollbackTargetVersionId;
    }

    public void setRollbackTargetVersionId(Long rollbackTargetVersionId) {
        this.rollbackTargetVersionId = rollbackTargetVersionId;
    }

    public Long getSourceCheckId() {
        return sourceCheckId;
    }

    public void setSourceCheckId(Long sourceCheckId) {
        this.sourceCheckId = sourceCheckId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getSnapshotChecksum() {
        return snapshotChecksum;
    }

    public void setSnapshotChecksum(String snapshotChecksum) {
        this.snapshotChecksum = snapshotChecksum;
    }

    public Long getSnapshotSizeBytes() {
        return snapshotSizeBytes;
    }

    public void setSnapshotSizeBytes(Long snapshotSizeBytes) {
        this.snapshotSizeBytes = snapshotSizeBytes;
    }

    public String getImpactReportJson() {
        return impactReportJson;
    }

    public void setImpactReportJson(String impactReportJson) {
        this.impactReportJson = impactReportJson;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }

    public String getPublishReason() {
        return publishReason;
    }

    public void setPublishReason(String publishReason) {
        this.publishReason = publishReason;
    }
}
