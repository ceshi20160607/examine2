package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台消息模板不可变发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_msg_template_version")
public class PlatMsgTemplateVersion {

    /**
     * 平台消息模板发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台消息模板主键
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 模板发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 被发布的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 发布时不可变的模板内容快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布模板的平台成员账号
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 模板发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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
        return "PlatMsgTemplateVersion{" +
            "id = " + id +
            ", templateId = " + templateId +
            ", platformId = " + platformId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishTime = " + publishTime +
            "}";
    }
}
