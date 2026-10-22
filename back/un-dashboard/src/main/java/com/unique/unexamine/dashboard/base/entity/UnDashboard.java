package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 自定义仪表盘
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_dashboard")
public class UnDashboard implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 仪表盘主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台系统或模块仪表盘作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统仪表盘所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统仪表盘所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 模块首页关联模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 个人仪表盘所属平台账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 作用域内仪表盘编码
     */
    @TableField("code")
    private String code;

    /**
     * 仪表盘名称
     */
    @TableField("name")
    private String name;

    /**
     * 草稿组件位置尺寸和页面布局
     */
    @TableField("layout_json")
    private String layoutJson;

    /**
     * 已发布页面布局快照
     */
    @TableField("published_layout_json")
    private String publishedLayoutJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布仪表盘版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 创建仪表盘的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public String getPublishedLayoutJson() {
        return publishedLayoutJson;
    }

    public void setPublishedLayoutJson(String publishedLayoutJson) {
        this.publishedLayoutJson = publishedLayoutJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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
}
