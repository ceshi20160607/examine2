package com.unique.examine.openapi.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_openapi_nonce")
public class OpenApiNonceEntity implements Serializable {
    @TableField("application_id")
    private Long applicationId;
    @TableField("credential_version")
    private Integer credentialVersion;
    @TableField("nonce")
    private String nonce;
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long value) { this.applicationId = value; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer value) { this.credentialVersion = value; }
    public String getNonce() { return nonce; }
    public void setNonce(String value) { this.nonce = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
