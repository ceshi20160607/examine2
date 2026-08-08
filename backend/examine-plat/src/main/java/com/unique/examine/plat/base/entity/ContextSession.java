package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@TableName("un_plat_context_session")
public class ContextSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("context_type")
    private String contextType;

    @TableField("account_id")
    private Long accountId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("member_id")
    private Long memberId;

    @TableField("permission_version")
    private Long permissionVersion;

    @TableField("authz_epoch")
    private Long authzEpoch;

    @TableField("permissions_json")
    private String permissionsJson;

    @TableField("role_snapshot_json")
    private String roleSnapshotJson;

    @TableField("data_scope_snapshot_json")
    private String dataScopeSnapshotJson;

    @TableField("status")
    private String status;

    @TableField("issued_at")
    private LocalDateTime issuedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
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

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public Long getAuthzEpoch() {
        return authzEpoch;
    }

    public void setAuthzEpoch(Long authzEpoch) {
        this.authzEpoch = authzEpoch;
    }

    public String getPermissionsJson() {
        return permissionsJson;
    }

    public void setPermissionsJson(String permissionsJson) {
        this.permissionsJson = permissionsJson;
    }

    public String getRoleSnapshotJson() {
        return roleSnapshotJson;
    }

    public void setRoleSnapshotJson(String roleSnapshotJson) {
        this.roleSnapshotJson = roleSnapshotJson;
    }

    public String getDataScopeSnapshotJson() {
        return dataScopeSnapshotJson;
    }

    public void setDataScopeSnapshotJson(String dataScopeSnapshotJson) {
        this.dataScopeSnapshotJson = dataScopeSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
