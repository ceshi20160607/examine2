package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("plat_account_credential")
public class PlatformAccountCredential {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("account_id")
    private Long accountId;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("password_algorithm")
    private String passwordAlgorithm;

    @TableField("must_change_password")
    private Boolean mustChangePassword;

    @TableField("failed_attempts")
    private Integer failedAttempts;

    @TableField("locked_until")
    private LocalDateTime lockedUntil;

    @TableField("credential_version")
    private Integer credentialVersion;

    @TableField("changed_at")
    private LocalDateTime changedAt;

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

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
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
            ", lockedUntil = " + lockedUntil +
            ", credentialVersion = " + credentialVersion +
            ", changedAt = " + changedAt +
            "}";
    }
}
