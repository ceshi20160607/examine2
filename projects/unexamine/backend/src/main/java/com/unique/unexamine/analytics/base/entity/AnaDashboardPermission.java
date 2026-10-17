package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 仪表盘可见及编辑授权，实际数据权限仍由运行态逐次校验
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard_permission")
public class AnaDashboardPermission {

    /**
     * 仪表盘可见权限主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表盘主键
     */
    @TableField("dashboard_id")
    private Long dashboardId;

    /**
     * 权限归属层级，与仪表盘一致
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 权限归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 权限所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统权限所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统角色或租户成员所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * VIEW 或 EDIT；运行时还需叠加数据和字段权限
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 平台或系统范围内的全员、角色或成员
     */
    @TableField("grantee_type")
    private String granteeType;

    /**
     * 被授权的平台角色主键
     */
    @TableField("platform_role_id")
    private Long platformRoleId;

    /**
     * 被授权的平台成员主键
     */
    @TableField("platform_member_id")
    private Long platformMemberId;

    /**
     * 被授权的租户角色主键
     */
    @TableField("system_role_id")
    private Long systemRoleId;

    /**
     * 被授权的租户成员主键
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 授权操作者账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 授权创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getGranteeType() {
        return granteeType;
    }

    public void setGranteeType(String granteeType) {
        this.granteeType = granteeType;
    }

    public Long getPlatformRoleId() {
        return platformRoleId;
    }

    public void setPlatformRoleId(Long platformRoleId) {
        this.platformRoleId = platformRoleId;
    }

    public Long getPlatformMemberId() {
        return platformMemberId;
    }

    public void setPlatformMemberId(Long platformMemberId) {
        this.platformMemberId = platformMemberId;
    }

    public Long getSystemRoleId() {
        return systemRoleId;
    }

    public void setSystemRoleId(Long systemRoleId) {
        this.systemRoleId = systemRoleId;
    }

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AnaDashboardPermission{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", permissionCode = " + permissionCode +
            ", granteeType = " + granteeType +
            ", platformRoleId = " + platformRoleId +
            ", platformMemberId = " + platformMemberId +
            ", systemRoleId = " + systemRoleId +
            ", tenantMemberId = " + tenantMemberId +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
