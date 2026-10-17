package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 受控密钥引用及当前版本，不保存密钥明文
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_secret_ref")
public class OpsSecretRef {

    /**
     * 受控密钥引用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 密钥归属层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统密钥所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 稳定归属主键，平台密钥为平台主键，系统密钥为系统主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 同一归属内稳定的密钥引用编码
     */
    @TableField("secret_code")
    private String secretCode;

    /**
     * 受控密钥服务提供方
     */
    @TableField("provider")
    private String provider;

    /**
     * 受控密钥服务中的引用路径，不是密钥明文
     */
    @TableField("reference_path")
    private String referencePath;

    /**
     * 当前启用的密钥版本标识
     */
    @TableField("current_version")
    private String currentVersion;

    /**
     * 密钥引用的启停状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 最近一次验证密钥可用性的时间
     */
    @TableField("last_verified_time")
    private LocalDateTime lastVerifiedTime;

    /**
     * 密钥引用创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 密钥引用更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 密钥引用乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

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

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
    }

    public String getSecretCode() {
        return secretCode;
    }

    public void setSecretCode(String secretCode) {
        this.secretCode = secretCode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getReferencePath() {
        return referencePath;
    }

    public void setReferencePath(String referencePath) {
        this.referencePath = referencePath;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastVerifiedTime() {
        return lastVerifiedTime;
    }

    public void setLastVerifiedTime(LocalDateTime lastVerifiedTime) {
        this.lastVerifiedTime = lastVerifiedTime;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsSecretRef{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", secretCode = " + secretCode +
            ", provider = " + provider +
            ", referencePath = " + referencePath +
            ", currentVersion = " + currentVersion +
            ", status = " + status +
            ", lastVerifiedTime = " + lastVerifiedTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
