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
 * 公共智能能力配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_common_ai")
public class UnCommonAi implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能能力配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统智能配置所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户专属智能配置主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作用域内智能能力编码
     */
    @TableField("code")
    private String code;

    /**
     * 智能能力名称
     */
    @TableField("name")
    private String name;

    /**
     * 模型提供方编码
     */
    @TableField("provider_code")
    private String providerCode;

    /**
     * 模型凭据安全引用
     */
    @TableField("secret_ref")
    private String secretRef;

    /**
     * 模型参数能力范围与人工确认策略
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布配置版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 运行态智能能力配置快照
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * 创建配置的平台账号主键
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
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
