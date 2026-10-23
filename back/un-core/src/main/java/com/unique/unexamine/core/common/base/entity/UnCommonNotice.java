package com.unique.unexamine.core.common.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 公共公告
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_common_notice")
public class UnCommonNotice implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公告主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统公告作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统公告所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户专属公告的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

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
     * 角色部门或成员可见范围
     */
    @TableField("audience_json")
    private String audienceJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 正式发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 公告失效时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 创建公告的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public String getAudienceJson() {
        return audienceJson;
    }

    public void setAudienceJson(String audienceJson) {
        this.audienceJson = audienceJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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
}
