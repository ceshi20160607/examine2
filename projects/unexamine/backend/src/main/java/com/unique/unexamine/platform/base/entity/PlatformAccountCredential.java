package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 账号口令凭据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_account_credential")
public class PlatformAccountCredential {

    /**
     * 账号口令凭据主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 口令哈希值
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 口令哈希算法
     */
    @TableField("password_algorithm")
    private String passwordAlgorithm;

    /**
     * 下次登录是否必须修改口令
     */
    @TableField("must_change_password")
    private Boolean mustChangePassword;

    /**
     * 连续登录失败次数
     */
    @TableField("failed_attempts")
    private Integer failedAttempts;

    /**
     * 账号口令锁定截止时间
     */
    @TableField("lock_expire_time")
    private LocalDateTime lockExpireTime;

    /**
     * 口令凭据版本号
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 口令最近变更时间
     */
    @TableField("change_time")
    private LocalDateTime changeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    public void setPasswordAlgorithm(String passwordAlgorithm) {
        this.passwordAlgorithm = passwordAlgorithm;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockExpireTime() {
        return lockExpireTime;
    }

    public void setLockExpireTime(LocalDateTime lockExpireTime) {
        this.lockExpireTime = lockExpireTime;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    @Override
    public String toString() {
        return "PlatformAccountCredential{" +
            "id = " + id +
            ", accountId = " + accountId +
            ", passwordHash = " + passwordHash +
            ", passwordAlgorithm = " + passwordAlgorithm +
            ", mustChangePassword = " + mustChangePassword +
            ", failedAttempts = " + failedAttempts +
            ", lockExpireTime = " + lockExpireTime +
            ", credentialVersion = " + credentialVersion +
            ", changeTime = " + changeTime +
            "}";
    }
}
