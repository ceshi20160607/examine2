package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户通知消息及跳转目标快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_msg_message")
public class MsgMessage {

    /**
     * 系统消息主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 固定为系统上下文
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 系统所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 消息所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 消息所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 模板消息的发布版本主键；内部事件消息为空
     */
    @TableField("template_version_id")
    private Long templateVersionId;

    /**
     * 通知来源业务类别
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源事件去重键，为空时不参与事件去重
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
     * 系统业务目标类别
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 系统业务目标标识
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 当前系统路由，打开前须复核成员及对象权限
     */
    @TableField("target_route")
    private String targetRoute;

    /**
     * 消息敏感级别
     */
    @TableField("sensitivity")
    private String sensitivity;

    /**
     * 发起通知的系统成员账号，自动通知可为空
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 发起通知的系统成员主键，自动通知可为空
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "MsgMessage{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
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
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            "}";
    }
}
