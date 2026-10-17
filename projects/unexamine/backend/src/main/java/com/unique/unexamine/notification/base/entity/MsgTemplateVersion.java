package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统消息模板不可变发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_msg_template_version")
public class MsgTemplateVersion {

    /**
     * 系统消息模板发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属消息模板主键
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 模板所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 模板所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

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
     * 发布模板的系统成员账号
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 发布模板的当前系统成员主键
     */
    @TableField("published_by_member_id")
    private Long publishedByMemberId;

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

    public Long getPublishedByMemberId() {
        return publishedByMemberId;
    }

    public void setPublishedByMemberId(Long publishedByMemberId) {
        this.publishedByMemberId = publishedByMemberId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "MsgTemplateVersion{" +
            "id = " + id +
            ", templateId = " + templateId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishTime = " + publishTime +
            "}";
    }
}
