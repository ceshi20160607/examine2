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
@TableName("plat_sso_identity")
public class PlatSsoIdentity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("provider_id")
    private Long providerId;

    @TableField("external_subject")
    private String externalSubject;

    @TableField("account_id")
    private Long accountId;

    @TableField("attributes_json")
    private String attributesJson;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public void setExternalSubject(String externalSubject) {
        this.externalSubject = externalSubject;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "PlatSsoIdentity{" +
            "id = " + id +
            ", providerId = " + providerId +
            ", externalSubject = " + externalSubject +
            ", accountId = " + accountId +
            ", attributesJson = " + attributesJson +
            ", lastLoginAt = " + lastLoginAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            "}";
    }
}
