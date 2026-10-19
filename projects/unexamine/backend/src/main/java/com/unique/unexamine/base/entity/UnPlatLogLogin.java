package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台登录日志
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_plat_log_login")
public class UnPlatLogLogin implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台登录日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 匹配到的平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 本次提交的登录账号
     */
    @TableField("login_account")
    private String loginAccount;

    /**
     * 登录是否成功：1 成功，0 失败
     */
    @TableField("success")
    private Byte success;

    /**
     * 登录失败原因
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * 发起登录的客户端地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 登录发生时间
     */
    @TableField("login_time")
    private LocalDateTime loginTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getLoginAccount() {
        return loginAccount;
    }

    public void setLoginAccount(String loginAccount) {
        this.loginAccount = loginAccount;
    }

    public Byte getSuccess() {
        return success;
    }

    public void setSuccess(Byte success) {
        this.success = success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}
