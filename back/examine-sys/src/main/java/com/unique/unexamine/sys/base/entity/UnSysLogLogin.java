package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统登录日志
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_sys_log_login")
public class UnSysLogLogin implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统登录日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 发起登录的业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 匹配到的系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 本次提交的系统登录账号
     */
    @TableField("login_account")
    private String loginAccount;

    /**
     * 登录是否成功：1成功、0失败
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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
