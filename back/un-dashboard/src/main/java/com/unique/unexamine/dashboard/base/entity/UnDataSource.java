package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 按模块及关联配置的自定义数据源
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_data_source")
public class UnDataSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自定义数据源主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统数据源作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统数据源所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义数据源的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作用域内稳定数据源编码
     */
    @TableField("code")
    private String code;

    /**
     * 数据源名称
     */
    @TableField("name")
    private String name;

    /**
     * 模块型数据源起始的已发布模块主键
     */
    @TableField("root_module_id")
    private Long rootModuleId;

    /**
     * 数据源业务口径说明
     */
    @TableField("description")
    private String description;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布数据源版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 创建数据源的平台账号主键
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

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public Long getRootModuleId() {
        return rootModuleId;
    }

    public void setRootModuleId(Long rootModuleId) {
        this.rootModuleId = rootModuleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
