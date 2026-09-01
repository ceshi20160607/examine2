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
@TableName("app_callback_delivery")
public class AppCallbackDelivery {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("callback_id")
    private Long callbackId;

    @TableField("event_id")
    private String eventId;

    @TableField("payload_hash")
    private String payloadHash;

    @TableField("`status`")
    private String status;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @TableField("response_status")
    private Integer responseStatus;

    @TableField("response_body_excerpt")
    private String responseBodyExcerpt;

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

    public Long getCallbackId() {
        return callbackId;
    }

    public void setCallbackId(Long callbackId) {
        this.callbackId = callbackId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBodyExcerpt() {
        return responseBodyExcerpt;
    }

    public void setResponseBodyExcerpt(String responseBodyExcerpt) {
        this.responseBodyExcerpt = responseBodyExcerpt;
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
        return "AppCallbackDelivery{" +
            "id = " + id +
            ", callbackId = " + callbackId +
            ", eventId = " + eventId +
            ", payloadHash = " + payloadHash +
            ", status = " + status +
            ", attemptCount = " + attemptCount +
            ", nextAttemptAt = " + nextAttemptAt +
            ", responseStatus = " + responseStatus +
            ", responseBodyExcerpt = " + responseBodyExcerpt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
