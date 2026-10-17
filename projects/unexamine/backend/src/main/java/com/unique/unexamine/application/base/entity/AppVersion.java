package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用发布时冻结的不可变配置版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_version")
public class AppVersion {

    /**
     * 应用版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 递增的发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布时的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 发布快照内容摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 冻结的应用、授权及回调配置
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布人的平台账号主键
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
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
        return "AppVersion{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotHash = " + snapshotHash +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishTime = " + publishTime +
            "}";
    }
}
