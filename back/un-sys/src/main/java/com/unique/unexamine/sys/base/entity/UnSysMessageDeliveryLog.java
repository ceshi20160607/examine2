package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统消息投递记录
 * </p>
 *
 * @author Template Base
 * @since 2026-11-07
 */
@TableName("un_sys_message_delivery_log")
public class UnSysMessageDeliveryLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统消息投递记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 使用的系统消息模板主键
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 接收系统成员主键
     */
    @TableField("recipient_member_id")
    private Long recipientMemberId;

    /**
     * 同一接收人业务事件的去重键，空表示不去重
     */
    @TableField("dedupe_key")
    private String dedupeKey;

    /**
     * 实际投递渠道
     */
    @TableField("channel")
    private String channel;

    /**
     * 投递结果状态
     */
    @TableField("status")
    private String status;

    /**
     * 成功投递的系统消息主键
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 投递尝试次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 失败原因摘要
     */
    @TableField("error_text")
    private String errorText;

    /**
     * 首次投递时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近投递时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    /**
     * 发送时渲染的标题快照
     */
    @TableField("rendered_title")
    private String renderedTitle;

    /**
     * 发送时渲染的正文快照
     */
    @TableField("rendered_content")
    private String renderedContent;

    /**
     * 发送时的系统业务跳转类型快照
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 发送时的系统业务跳转主键快照
     */
    @TableField("target_id")
    private Long targetId;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getRecipientMemberId() {
        return recipientMemberId;
    }

    public void setRecipientMemberId(Long recipientMemberId) {
        this.recipientMemberId = recipientMemberId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }

    public String getRenderedTitle() {
        return renderedTitle;
    }

    public void setRenderedTitle(String renderedTitle) {
        this.renderedTitle = renderedTitle;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public void setRenderedContent(String renderedContent) {
        this.renderedContent = renderedContent;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }
}
