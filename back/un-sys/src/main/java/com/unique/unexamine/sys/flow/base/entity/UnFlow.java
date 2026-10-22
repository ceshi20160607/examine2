package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统流程定义
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_flow")
public class UnFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程定义主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统流程作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统流程所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统流程定义租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作用域内稳定流程编码
     */
    @TableField("code")
    private String code;

    /**
     * 流程名称
     */
    @TableField("name")
    private String name;

    /**
     * 流程用途说明
     */
    @TableField("description")
    private String description;

    /**
     * 草稿超时撤回拒绝重审等高级配置
     */
    @TableField("settings_json")
    private String settingsJson;

    /**
     * 草稿已发布停用或归档状态
     */
    @TableField("status")
    private String status;

    /**
     * 最近发布的不可变流程版本号
     */
    @TableField("latest_version")
    private Integer latestVersion;

    /**
     * 创建流程的平台账号主键
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

    public String getSettingsJson() {
        return settingsJson;
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(Integer latestVersion) {
        this.latestVersion = latestVersion;
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
