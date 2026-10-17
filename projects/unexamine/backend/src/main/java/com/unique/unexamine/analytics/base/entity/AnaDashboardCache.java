package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 按发布版本、访问者、租户和权限口径隔离的仪表盘组件缓存
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard_cache")
public class AnaDashboardCache {

    /**
     * 仪表盘组件缓存主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表盘主键
     */
    @TableField("dashboard_id")
    private Long dashboardId;

    /**
     * 缓存对应的不可变仪表盘发布版本主键
     */
    @TableField("dashboard_version_id")
    private Long dashboardVersionId;

    /**
     * 仪表盘归属层级
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 仪表盘归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 当前访问平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 当前访问系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 当前访问租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 缓存所属访问者账号主键，防止跨人共享
     */
    @TableField("viewer_account_id")
    private Long viewerAccountId;

    /**
     * 平台访问者账号主键，系统缓存为空以免强制系统成员同时是平台成员
     */
    @TableField("viewer_platform_account_id")
    private Long viewerPlatformAccountId;

    /**
     * 系统访问者在当前系统的成员主键
     */
    @TableField("viewer_system_member_id")
    private Long viewerSystemMemberId;

    /**
     * 发布快照中的组件编码
     */
    @TableField("component_key")
    private String componentKey;

    /**
     * 当前角色、字段及数据范围权限指纹
     */
    @TableField("permission_hash")
    private String permissionHash;

    /**
     * 筛选、时间范围及统计口径指纹
     */
    @TableField("query_hash")
    private String queryHash;

    /**
     * 已脱敏且限于当前权限的数据结果快照
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 缓存刷新状态
     */
    @TableField("refresh_status")
    private String refreshStatus;

    /**
     * 缓存失效时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 缓存创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 缓存刷新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getDashboardVersionId() {
        return dashboardVersionId;
    }

    public void setDashboardVersionId(Long dashboardVersionId) {
        this.dashboardVersionId = dashboardVersionId;
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

    public Long getViewerAccountId() {
        return viewerAccountId;
    }

    public void setViewerAccountId(Long viewerAccountId) {
        this.viewerAccountId = viewerAccountId;
    }

    public Long getViewerPlatformAccountId() {
        return viewerPlatformAccountId;
    }

    public void setViewerPlatformAccountId(Long viewerPlatformAccountId) {
        this.viewerPlatformAccountId = viewerPlatformAccountId;
    }

    public Long getViewerSystemMemberId() {
        return viewerSystemMemberId;
    }

    public void setViewerSystemMemberId(Long viewerSystemMemberId) {
        this.viewerSystemMemberId = viewerSystemMemberId;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public String getPermissionHash() {
        return permissionHash;
    }

    public void setPermissionHash(String permissionHash) {
        this.permissionHash = permissionHash;
    }

    public String getQueryHash() {
        return queryHash;
    }

    public void setQueryHash(String queryHash) {
        this.queryHash = queryHash;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getRefreshStatus() {
        return refreshStatus;
    }

    public void setRefreshStatus(String refreshStatus) {
        this.refreshStatus = refreshStatus;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
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
        return "AnaDashboardCache{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", dashboardVersionId = " + dashboardVersionId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", viewerAccountId = " + viewerAccountId +
            ", viewerPlatformAccountId = " + viewerPlatformAccountId +
            ", viewerSystemMemberId = " + viewerSystemMemberId +
            ", componentKey = " + componentKey +
            ", permissionHash = " + permissionHash +
            ", queryHash = " + queryHash +
            ", payloadJson = " + payloadJson +
            ", refreshStatus = " + refreshStatus +
            ", expireTime = " + expireTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
