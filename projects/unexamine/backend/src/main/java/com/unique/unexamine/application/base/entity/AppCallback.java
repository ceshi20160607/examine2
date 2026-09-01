package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_callback")
public class AppCallback {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("callback_type")
    private String callbackType;

    @TableField("url")
    private String url;

    @TableField("event_codes_json")
    private String eventCodesJson;

    @TableField("signing_secret_ref")
    private String signingSecretRef;

    @TableField("timeout_millis")
    private Integer timeoutMillis;

    @TableField("max_attempts")
    private Integer maxAttempts;

    @TableField("`status`")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getCallbackType() {
        return callbackType;
    }

    public void setCallbackType(String callbackType) {
        this.callbackType = callbackType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEventCodesJson() {
        return eventCodesJson;
    }

    public void setEventCodesJson(String eventCodesJson) {
        this.eventCodesJson = eventCodesJson;
    }

    public String getSigningSecretRef() {
        return signingSecretRef;
    }

    public void setSigningSecretRef(String signingSecretRef) {
        this.signingSecretRef = signingSecretRef;
    }

    public Integer getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppCallback{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", callbackType = " + callbackType +
            ", url = " + url +
            ", eventCodesJson = " + eventCodesJson +
            ", signingSecretRef = " + signingSecretRef +
            ", timeoutMillis = " + timeoutMillis +
            ", maxAttempts = " + maxAttempts +
            ", status = " + status +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
