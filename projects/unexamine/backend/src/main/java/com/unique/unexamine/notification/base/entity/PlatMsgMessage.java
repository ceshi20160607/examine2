package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台自身通知消息及受限跳转目标
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_msg_message")
public class PlatMsgMessage {

    /**
     * 平台消息主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 模板消息的发布版本主键；平台内部事件消息为空
     */
    @TableField("template_version_id")
    private Long templateVersionId;

    /**
     * 平台通知来源类别
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源事件去重键，为空时不参与去重
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 发送时渲染的主题快照
     */
    @TableField("`subject`")
    private String subject;

    /**
     * 发送时渲染的正文快照
     */
    @TableField("content_text")
    private String contentText;

    /**
     * 平台目标类别，不得指向系统业务详情
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 平台目标标识或系统切换提示标识
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 平台路由或系统切换入口
     */
    @TableField("target_route")
    private String targetRoute;

    /**
     * 消息敏感级别
     */
    @TableField("sensitivity")
    private String sensitivity;

    /**
     * 发起通知的平台成员账号，自动通知可为空
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 消息创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public void setTargetRoute(String targetRoute) {
        this.targetRoute = targetRoute;
    }

    public String getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(String sensitivity) {
        this.sensitivity = sensitivity;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "PlatMsgMessage{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", templateVersionId = " + templateVersionId +
            ", sourceType = " + sourceType +
            ", sourceId = " + sourceId +
            ", subject = " + subject +
            ", contentText = " + contentText +
            ", targetType = " + targetType +
            ", targetId = " + targetId +
            ", targetRoute = " + targetRoute +
            ", sensitivity = " + sensitivity +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
