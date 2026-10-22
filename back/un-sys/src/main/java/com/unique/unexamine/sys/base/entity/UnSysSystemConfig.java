package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 业务系统基础配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_sys_system_config")
public class UnSysSystemConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统基础配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 多租户模式开关，关闭时保留非主租户数据
     */
    @TableField("multi_tenant_enabled")
    private Boolean multiTenantEnabled;

    /**
     * 系统登录展示标题
     */
    @TableField("login_title")
    private String loginTitle;

    /**
     * 系统专属登录域名
     */
    @TableField("login_domain")
    private String loginDomain;

    /**
     * 系统默认首页路由
     */
    @TableField("default_home")
    private String defaultHome;

    /**
     * 已配置系统功能开关和参数
     */
    @TableField("settings_json")
    private String settingsJson;

    /**
     * 最近修改平台账号主键
     */
    @TableField("update_account_id")
    private Long updateAccountId;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Boolean getMultiTenantEnabled() {
        return multiTenantEnabled;
    }

    public void setMultiTenantEnabled(Boolean multiTenantEnabled) {
        this.multiTenantEnabled = multiTenantEnabled;
    }

    public String getLoginTitle() {
        return loginTitle;
    }

    public void setLoginTitle(String loginTitle) {
        this.loginTitle = loginTitle;
    }

    public String getLoginDomain() {
        return loginDomain;
    }

    public void setLoginDomain(String loginDomain) {
        this.loginDomain = loginDomain;
    }

    public String getDefaultHome() {
        return defaultHome;
    }

    public void setDefaultHome(String defaultHome) {
        this.defaultHome = defaultHome;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson;
    }

    public Long getUpdateAccountId() {
        return updateAccountId;
    }

    public void setUpdateAccountId(Long updateAccountId) {
        this.updateAccountId = updateAccountId;
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
