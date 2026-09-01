package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_call")
public class AppCall {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("credential_version")
    private Integer credentialVersion;

    @TableField("grant_id")
    private Long grantId;

    @TableField("request_id")
    private String requestId;

    @TableField("trace_id")
    private String traceId;

    @TableField("request_timestamp")
    private LocalDateTime requestTimestamp;

    @TableField("nonce")
    private String nonce;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("source_address")
    private String sourceAddress;

    @TableField("target_system_id")
    private Long targetSystemId;

    @TableField("target_tenant_id")
    private Long targetTenantId;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("action_code")
    private String actionCode;

    @TableField("request_hash")
    private String requestHash;

    @TableField("permission_snapshot_json")
    private String permissionSnapshotJson;

    @TableField("`status`")
    private String status;

    @TableField("response_code")
    private String responseCode;

    @TableField("response_json")
    private String responseJson;

    @TableField("target_reference")
    private String targetReference;

    @TableField("replay_count")
    private Integer replayCount;

    @TableField("duration_millis")
    private Long durationMillis;

    @TableField("error_message")
    private String errorMessage;

    @TableField("called_at")
    private LocalDateTime calledAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

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

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
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

    public LocalDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(LocalDateTime requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public Long getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(Long targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getPermissionSnapshotJson() {
        return permissionSnapshotJson;
    }

    public void setPermissionSnapshotJson(String permissionSnapshotJson) {
        this.permissionSnapshotJson = permissionSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public void setTargetReference(String targetReference) {
        this.targetReference = targetReference;
    }

    public Integer getReplayCount() {
        return replayCount;
    }

    public void setReplayCount(Integer replayCount) {
        this.replayCount = replayCount;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Override
    public String toString() {
        return "AppCall{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", credentialVersion = " + credentialVersion +
            ", grantId = " + grantId +
            ", requestId = " + requestId +
            ", traceId = " + traceId +
            ", requestTimestamp = " + requestTimestamp +
            ", nonce = " + nonce +
            ", idempotencyKey = " + idempotencyKey +
            ", sourceAddress = " + sourceAddress +
            ", targetSystemId = " + targetSystemId +
            ", targetTenantId = " + targetTenantId +
            ", resourceType = " + resourceType +
            ", resourceId = " + resourceId +
            ", actionCode = " + actionCode +
            ", requestHash = " + requestHash +
            ", permissionSnapshotJson = " + permissionSnapshotJson +
            ", status = " + status +
            ", responseCode = " + responseCode +
            ", responseJson = " + responseJson +
            ", targetReference = " + targetReference +
            ", replayCount = " + replayCount +
            ", durationMillis = " + durationMillis +
            ", errorMessage = " + errorMessage +
            ", calledAt = " + calledAt +
            ", finishedAt = " + finishedAt +
            "}";
    }
}
