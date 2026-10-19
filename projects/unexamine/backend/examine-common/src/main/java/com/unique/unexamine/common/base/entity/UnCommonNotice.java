package com.unique.unexamine.common.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 公共公告
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_common_notice")
public class UnCommonNotice implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公共公告主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 公告范围：platform、system或tenant
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 所属系统或租户主键，平台范围为0
     */
    @TableField("scope_id")
    private Long scopeId;

    /**
     * 公告标题
     */
    @TableField("title")
    private String title;

    /**
     * 公告正文
     */
    @TableField("content")
    private String content;

    /**
     * 公告状态：0草稿、1已发布、2已撤销
     */
    @TableField("status")
    private Byte status;

    /**
     * 发布人对应范围内的成员主键
     */
    @TableField("publisher_id")
    private Long publisherId;

    /**
     * 公告发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 公告过期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
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

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
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
}
