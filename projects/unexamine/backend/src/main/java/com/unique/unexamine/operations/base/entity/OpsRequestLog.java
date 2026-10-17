package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不含敏感请求内容的调用观测与追踪记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_request_log")
public class OpsRequestLog {

    /**
     * 请求观测记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对外请求标识，用于定位一次调用
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 跨服务调用链追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 系统上下文主键，平台请求为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户上下文主键，平台或系统公共请求为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 已识别的平台账号主键，匿名请求为空
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 发起请求的客户端渠道
     */
    @TableField("client_source")
    private String clientSource;

    /**
     * HTTP 请求方法
     */
    @TableField("http_method")
    private String httpMethod;

    /**
     * 不含查询串的请求路径
     */
    @TableField("request_path")
    private String requestPath;

    /**
     * HTTP 响应状态码
     */
    @TableField("status_code")
    private Integer statusCode;

    /**
     * 应用层结果或错误编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 请求处理耗时，单位毫秒
     */
    @TableField("duration_millis")
    private Long durationMillis;

    /**
     * 请求发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public String getClientSource() {
        return clientSource;
    }

    public void setClientSource(String clientSource) {
        this.clientSource = clientSource;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }

    @Override
    public String toString() {
        return "OpsRequestLog{" +
            "id = " + id +
            ", requestId = " + requestId +
            ", traceId = " + traceId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", accountId = " + accountId +
            ", clientSource = " + clientSource +
            ", httpMethod = " + httpMethod +
            ", requestPath = " + requestPath +
            ", statusCode = " + statusCode +
            ", resultCode = " + resultCode +
            ", durationMillis = " + durationMillis +
            ", occurTime = " + occurTime +
            "}";
    }
}
