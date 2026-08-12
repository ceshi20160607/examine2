package com.unique.examine.openapi.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_openapi_credential")
public class OpenApiCredentialEntity implements Serializable {
    @TableId("id")
    private Long id;
    @TableField("application_id")
    private Long applicationId;
    @TableField("credential_version")
    private Integer credentialVersion;
    @TableField("secret_ref")
    private String secretRef;
    @TableField("status")
    private String status;
    @TableField("activated_at")
    private LocalDateTime activatedAt;
    @TableField("revoked_at")
    private LocalDateTime revokedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("created_by")
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer value) { this.credentialVersion = value; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime value) { this.activatedAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
