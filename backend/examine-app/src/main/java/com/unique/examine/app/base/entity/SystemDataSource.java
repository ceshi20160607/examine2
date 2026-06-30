package com.unique.examine.app.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Table-backed system data source configuration.
 */
@TableName("un_system_data_source")
public class SystemDataSource {

    public static final String TABLE_NAME = "un_system_data_source";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long systemId;
    private Long tenantId;
    private String sourceCode;
    private String sourceName;
    private String sourceType;
    private String connectionConfig;
    private String authConfig;
    private String syncConfig;
    private String desensitizeConfig;
    private Integer status;
    private String publishStatus;
    private String publishedVersion;
    private String lastCheckStatus;
    private String lastCheckTraceId;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

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

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(String connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(String authConfig) {
        this.authConfig = authConfig;
    }

    public String getSyncConfig() {
        return syncConfig;
    }

    public void setSyncConfig(String syncConfig) {
        this.syncConfig = syncConfig;
    }

    public String getDesensitizeConfig() {
        return desensitizeConfig;
    }

    public void setDesensitizeConfig(String desensitizeConfig) {
        this.desensitizeConfig = desensitizeConfig;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public String getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(String publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public String getLastCheckStatus() {
        return lastCheckStatus;
    }

    public void setLastCheckStatus(String lastCheckStatus) {
        this.lastCheckStatus = lastCheckStatus;
    }

    public String getLastCheckTraceId() {
        return lastCheckTraceId;
    }

    public void setLastCheckTraceId(String lastCheckTraceId) {
        this.lastCheckTraceId = lastCheckTraceId;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
