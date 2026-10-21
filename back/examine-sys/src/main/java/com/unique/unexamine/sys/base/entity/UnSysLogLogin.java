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
 * @since 2026-10-21
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
     * 访问业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 访问租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 登录平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 系统登录结果编码
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
     * 请求追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 登录时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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
