package com.unique.unexamine.application.base.entity;

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
@TableName("app_credential")
public class AppCredential {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("credential_version")
    private Integer credentialVersion;

    @TableField("client_id")
    private String clientId;

    @TableField("secret_hash")
    private String secretHash;

    @TableField("signing_secret_ref")
    private String signingSecretRef;

    @TableField("secret_hint")
    private String secretHint;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("`status`")
    private String status;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("created_by_account_id")
    private Long createdByAccountId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSigningSecretRef() {
        return signingSecretRef;
    }

    public void setSigningSecretRef(String signingSecretRef) {
        this.signingSecretRef = signingSecretRef;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public void setSecretHint(String secretHint) {
        this.secretHint = secretHint;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AppCredential{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", credentialVersion = " + credentialVersion +
            ", clientId = " + clientId +
            ", secretHash = " + secretHash +
            ", signingSecretRef = " + signingSecretRef +
            ", secretHint = " + secretHint +
            ", validFrom = " + validFrom +
            ", expiresAt = " + expiresAt +
            ", status = " + status +
            ", revokedAt = " + revokedAt +
            ", createdByAccountId = " + createdByAccountId +
            ", createdAt = " + createdAt +
            "}";
    }
}
