package com.unique.examine.openapi.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_openapi_application")
public class OpenApiApplicationEntity implements Serializable {
    @TableId("id")
    private Long id;
    @TableField("system_id")
    private Long systemId;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("service_member_id")
    private Long serviceMemberId;
    @TableField("app_key")
    private String appKey;
    @TableField("name")
    private String name;
    @TableField("status")
    private String status;
    @TableField("scopes_json")
    private String scopesJson;
    @TableField("ip_allowlist_json")
    private String ipAllowlistJson;
    @TableField("rate_limit_per_minute")
    private Integer rateLimitPerMinute;
    @TableField("current_credential_version")
    private Integer currentCredentialVersion;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("created_by")
    private Long createdBy;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
    @TableField("version")
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getServiceMemberId() { return serviceMemberId; }
    public void setServiceMemberId(Long serviceMemberId) { this.serviceMemberId = serviceMemberId; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScopesJson() { return scopesJson; }
    public void setScopesJson(String scopesJson) { this.scopesJson = scopesJson; }
    public String getIpAllowlistJson() { return ipAllowlistJson; }
    public void setIpAllowlistJson(String ipAllowlistJson) { this.ipAllowlistJson = ipAllowlistJson; }
    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer value) { this.rateLimitPerMinute = value; }
    public Integer getCurrentCredentialVersion() { return currentCredentialVersion; }
    public void setCurrentCredentialVersion(Integer value) { this.currentCredentialVersion = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
