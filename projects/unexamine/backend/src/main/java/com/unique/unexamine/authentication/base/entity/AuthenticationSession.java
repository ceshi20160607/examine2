package com.unique.unexamine.authentication.base.entity;

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
@TableName("auth_session")
public class AuthenticationSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("access_token_hash")
    private String accessTokenHash;

    @TableField("refresh_token_hash")
    private String refreshTokenHash;

    @TableField("account_id")
    private Long accountId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("member_id")
    private Long memberId;

    @TableField("role_ids_json")
    private String roleIdsJson;

    @TableField("action_permissions_json")
    private String actionPermissionsJson;

    @TableField("data_scopes_json")
    private String dataScopesJson;

    @TableField("access_expires_at")
    private LocalDateTime accessExpiresAt;

    @TableField("refresh_expires_at")
    private LocalDateTime refreshExpiresAt;

    @TableField("revoked")
    private Boolean revoked;

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

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getRoleIdsJson() {
        return roleIdsJson;
    }

    public void setRoleIdsJson(String roleIdsJson) {
        this.roleIdsJson = roleIdsJson;
    }

    public String getActionPermissionsJson() {
        return actionPermissionsJson;
    }

    public void setActionPermissionsJson(String actionPermissionsJson) {
        this.actionPermissionsJson = actionPermissionsJson;
    }

    public String getDataScopesJson() {
        return dataScopesJson;
    }

    public void setDataScopesJson(String dataScopesJson) {
        this.dataScopesJson = dataScopesJson;
    }

    public LocalDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(LocalDateTime accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public LocalDateTime getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public void setRefreshExpiresAt(LocalDateTime refreshExpiresAt) {
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
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
        return "AuthenticationSession{" +
            "id = " + id +
            ", accessTokenHash = " + accessTokenHash +
            ", refreshTokenHash = " + refreshTokenHash +
            ", accountId = " + accountId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", memberId = " + memberId +
            ", roleIdsJson = " + roleIdsJson +
            ", actionPermissionsJson = " + actionPermissionsJson +
            ", dataScopesJson = " + dataScopesJson +
            ", accessExpiresAt = " + accessExpiresAt +
            ", refreshExpiresAt = " + refreshExpiresAt +
            ", revoked = " + revoked +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            "}";
    }
}
