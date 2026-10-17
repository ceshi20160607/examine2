package com.unique.unexamine.authentication.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 登录会话
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_auth_session")
public class AuthenticationSession {

    /**
     * 登录会话主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 访问令牌哈希值
     */
    @TableField("access_token_hash")
    private String accessTokenHash;

    /**
     * 刷新令牌哈希值
     */
    @TableField("refresh_token_hash")
    private String refreshTokenHash;

    /**
     * 登录的平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 当前平台范围标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 当前业务系统范围标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 当前租户范围标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 当前系统成员标识
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 当前租户成员标识
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 会话生效角色标识快照
     */
    @TableField("role_ids_json")
    private String roleIdsJson;

    /**
     * 会话动作权限快照
     */
    @TableField("action_permissions_json")
    private String actionPermissionsJson;

    /**
     * 会话数据范围快照
     */
    @TableField("data_scopes_json")
    private String dataScopesJson;

    /**
     * 会话权限快照版本号
     */
    @TableField("permission_version")
    private Long permissionVersion;

    /**
     * 登录通过的多因素认证级别
     */
    @TableField("mfa_level")
    private String mfaLevel;

    /**
     * 访问令牌失效时间
     */
    @TableField("access_expire_time")
    private LocalDateTime accessExpireTime;

    /**
     * 刷新令牌失效时间
     */
    @TableField("refresh_expire_time")
    private LocalDateTime refreshExpireTime;

    /**
     * 会话是否已撤销
     */
    @TableField("revoked")
    private Boolean revoked;

    /**
     * 会话撤销原因
     */
    @TableField("revoked_reason")
    private String revokedReason;

    /**
     * 登录会话创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 登录会话更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
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

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public String getMfaLevel() {
        return mfaLevel;
    }

    public void setMfaLevel(String mfaLevel) {
        this.mfaLevel = mfaLevel;
    }

    public LocalDateTime getAccessExpireTime() {
        return accessExpireTime;
    }

    public void setAccessExpireTime(LocalDateTime accessExpireTime) {
        this.accessExpireTime = accessExpireTime;
    }

    public LocalDateTime getRefreshExpireTime() {
        return refreshExpireTime;
    }

    public void setRefreshExpireTime(LocalDateTime refreshExpireTime) {
        this.refreshExpireTime = refreshExpireTime;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
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

    @Override
    public String toString() {
        return "AuthenticationSession{" +
            "id = " + id +
            ", accessTokenHash = " + accessTokenHash +
            ", refreshTokenHash = " + refreshTokenHash +
            ", accountId = " + accountId +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", memberId = " + memberId +
            ", tenantMemberId = " + tenantMemberId +
            ", roleIdsJson = " + roleIdsJson +
            ", actionPermissionsJson = " + actionPermissionsJson +
            ", dataScopesJson = " + dataScopesJson +
            ", permissionVersion = " + permissionVersion +
            ", mfaLevel = " + mfaLevel +
            ", accessExpireTime = " + accessExpireTime +
            ", refreshExpireTime = " + refreshExpireTime +
            ", revoked = " + revoked +
            ", revokedReason = " + revokedReason +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
