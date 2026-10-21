package com.unique.unexamine.plat.base.entity;

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
 * @since 2026-10-21
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
     * 匹配的平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 尝试登录名
     */
    @TableField("username")
    private String username;

    /**
     * 登录结果编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 失败原因
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * 请求地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 客户端标识
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 请求追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 登录尝试时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
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

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
