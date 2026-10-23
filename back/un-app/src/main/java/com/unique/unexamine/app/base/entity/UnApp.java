package com.unique.unexamine.app.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统受控访问应用
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_app")
public class UnApp implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 受控应用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台应用或系统应用
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统应用所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统应用所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 对外稳定应用标识
     */
    @TableField("app_id")
    private String appId;

    /**
     * 应用名称
     */
    @TableField("name")
    private String name;

    /**
     * 应用业务说明
     */
    @TableField("description")
    private String description;

    /**
     * 应用展示图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 内部外部或同时允许的访问模式
     */
    @TableField("access_mode")
    private String accessMode;

    /**
     * 当前密钥安全引用
     */
    @TableField("secret_ref")
    private String secretRef;

    /**
     * 当前密钥版本
     */
    @TableField("secret_version")
    private Integer secretVersion;

    /**
     * 轮换过渡期旧密钥安全引用
     */
    @TableField("previous_secret_ref")
    private String previousSecretRef;

    /**
     * 轮换过渡期旧密钥失效时间
     */
    @TableField("previous_secret_expire_time")
    private LocalDateTime previousSecretExpireTime;

    /**
     * 应用回调地址
     */
    @TableField("callback_url")
    private String callbackUrl;

    /**
     * 草稿资源动作字段数据范围及访问控制配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 已发布的应用授权和访问控制快照
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布应用配置版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 创建应用的平台账号主键
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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public Integer getSecretVersion() {
        return secretVersion;
    }

    public void setSecretVersion(Integer secretVersion) {
        this.secretVersion = secretVersion;
    }

    public String getPreviousSecretRef() {
        return previousSecretRef;
    }

    public void setPreviousSecretRef(String previousSecretRef) {
        this.previousSecretRef = previousSecretRef;
    }

    public LocalDateTime getPreviousSecretExpireTime() {
        return previousSecretExpireTime;
    }

    public void setPreviousSecretExpireTime(LocalDateTime previousSecretExpireTime) {
        this.previousSecretExpireTime = previousSecretExpireTime;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
