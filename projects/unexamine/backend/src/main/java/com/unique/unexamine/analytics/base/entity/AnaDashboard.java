package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台、系统、模块或个人仪表盘草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard")
public class AnaDashboard {

    /**
     * 仪表盘主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 运行上下文，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统仪表盘所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户扩展仪表盘所属租户主键
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 唯一归属层级，PLATFORM、SYSTEM 或 TENANT
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 归属层级对应的平台、系统或租户主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 平台首页、系统首页、模块首页或个人首页
     */
    @TableField("dashboard_kind")
    private String dashboardKind;

    /**
     * 模块首页绑定的同系统模块主键，租户使用共享模块时运行态复核发布与共享权限
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 个人仪表盘所有者账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 同一归属层级内稳定的仪表盘编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 仪表盘显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 仪表盘用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 当前可视化草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 草稿、已发布或停用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建者账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
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

    public String getDashboardKind() {
        return dashboardKind;
    }

    public void setDashboardKind(String dashboardKind) {
        this.dashboardKind = dashboardKind;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AnaDashboard{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", dashboardKind = " + dashboardKind +
            ", moduleId = " + moduleId +
            ", ownerAccountId = " + ownerAccountId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", draftRevision = " + draftRevision +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
