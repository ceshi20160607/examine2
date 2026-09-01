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
@TableName("app_call_nonce")
public class AppCallNonce {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("credential_version")
    private Integer credentialVersion;

    @TableField("nonce")
    private String nonce;

    @TableField("request_id")
    private String requestId;

    @TableField("request_timestamp")
    private LocalDateTime requestTimestamp;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

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

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(LocalDateTime requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AppCallNonce{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", credentialVersion = " + credentialVersion +
            ", nonce = " + nonce +
            ", requestId = " + requestId +
            ", requestTimestamp = " + requestTimestamp +
            ", expiresAt = " + expiresAt +
            ", createdAt = " + createdAt +
            "}";
    }
}
