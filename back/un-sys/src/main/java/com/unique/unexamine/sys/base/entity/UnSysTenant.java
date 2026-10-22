package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_sys_tenant")
public class UnSysTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统租户主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统内租户编码
     */
    @TableField("code")
    private String code;

    /**
     * 租户名称
     */
    @TableField("name")
    private String name;

    /**
     * 是否默认主租户
     */
    @TableField("is_root")
    private Boolean isRoot;

    /**
     * 租户自身启停状态
     */
    @TableField("status")
    private String status;

    /**
     * 租户专属登录域名
     */
    @TableField("login_domain")
    private String loginDomain;

    /**
     * 租户登录展示配置
     */
    @TableField("display_json")
    private String displayJson;

    /**
     * 租户成员模块字段存储等配额
     */
    @TableField("quota_json")
    private String quotaJson;

    /**
     * 创建平台账号主键
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
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

    public Boolean getIsRoot() {
        return isRoot;
    }

    public void setIsRoot(Boolean isRoot) {
        this.isRoot = isRoot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLoginDomain() {
        return loginDomain;
    }

    public void setLoginDomain(String loginDomain) {
        this.loginDomain = loginDomain;
    }

    public String getDisplayJson() {
        return displayJson;
    }

    public void setDisplayJson(String displayJson) {
        this.displayJson = displayJson;
    }

    public String getQuotaJson() {
        return quotaJson;
    }

    public void setQuotaJson(String quotaJson) {
        this.quotaJson = quotaJson;
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
