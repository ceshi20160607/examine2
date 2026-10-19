package com.unique.unexamine.identity.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台消息
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_plat_message")
public class UnPlatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台消息主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 接收消息的平台成员主键
     */
    @TableField("recipient_member_id")
    private Long recipientMemberId;

    /**
     * 消息标题
     */
    @TableField("title")
    private String title;

    /**
     * 消息正文
     */
    @TableField("content")
    private String content;

    /**
     * 消息业务类型
     */
    @TableField("message_type")
    private String messageType;

    /**
     * 关联业务对象类型
     */
    @TableField("related_type")
    private String relatedType;

    /**
     * 关联业务对象主键
     */
    @TableField("related_id")
    private Long relatedId;

    /**
     * 接收人阅读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

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

    public Long getRecipientMemberId() {
        return recipientMemberId;
    }

    public void setRecipientMemberId(Long recipientMemberId) {
        this.recipientMemberId = recipientMemberId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
