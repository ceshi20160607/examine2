package com.unique.examine.openapi.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_openapi_call_log")
public class OpenApiCallLogEntity implements Serializable {
    @TableId("id")
    private Long id;
    @TableField("application_id")
    private Long applicationId;
    @TableField("app_key_hash")
    private String appKeyHash;
    @TableField("credential_version")
    private Integer credentialVersion;
    @TableField("route_template")
    private String routeTemplate;
    @TableField("request_method")
    private String requestMethod;
    @TableField("result_category")
    private String resultCategory;
    @TableField("http_status")
    private Integer httpStatus;
    @TableField("latency_ms")
    private Long latencyMs;
    @TableField("request_id")
    private String requestId;
    @TableField("trace_id")
    private String traceId;
    @TableField("observed_ip")
    private byte[] observedIp;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long value) { this.applicationId = value; }
    public String getAppKeyHash() { return appKeyHash; }
    public void setAppKeyHash(String value) { this.appKeyHash = value; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer value) { this.credentialVersion = value; }
    public String getRouteTemplate() { return routeTemplate; }
    public void setRouteTemplate(String value) { this.routeTemplate = value; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String value) { this.requestMethod = value; }
    public String getResultCategory() { return resultCategory; }
    public void setResultCategory(String value) { this.resultCategory = value; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer value) { this.httpStatus = value; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long value) { this.latencyMs = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String value) { this.traceId = value; }
    public byte[] getObservedIp() { return observedIp == null ? null : observedIp.clone(); }
    public void setObservedIp(byte[] value) { this.observedIp = value == null ? null : value.clone(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
