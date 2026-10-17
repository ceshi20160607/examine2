package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变仪表盘发布版本及组件快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard_version")
public class AnaDashboardVersion {

    /**
     * 仪表盘发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表盘主键
     */
    @TableField("dashboard_id")
    private Long dashboardId;

    /**
     * 发布时固定的归属层级
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 发布时固定的归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 递增发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布时采用的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 仪表盘发布快照的 SHA-256 摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 不可变组件、布局及数据源版本快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布者账号主键
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
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

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "AnaDashboardVersion{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotHash = " + snapshotHash +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishTime = " + publishTime +
            "}";
    }
}
