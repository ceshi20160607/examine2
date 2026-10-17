package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用调用审计与幂等结果
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_call")
public class AppCall {

    /**
     * 调用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被调用的目标应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 实际执行的不可变应用发布版本主键，前置拒绝时为空
     */
    @TableField("application_version_id")
    private Long applicationVersionId;

    /**
     * 验签所用凭证版本，内部可信调用为0
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 命中的草稿授权主键，前置拒绝或授权历史清理后为空
     */
    @TableField("grant_id")
    private Long grantId;

    /**
     * 调用请求唯一标识
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 跨服务追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 调用方签名请求时间
     */
    @TableField("request_time")
    private LocalDateTime requestTime;

    /**
     * 本次签名请求的防重放随机值
     */
    @TableField("nonce")
    private String nonce;

    /**
     * 调用方提供的幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 调用方网络地址
     */
    @TableField("source_address")
    private String sourceAddress;

    /**
     * 请求指向的目标系统主键
     */
    @TableField("target_system_id")
    private Long targetSystemId;

    /**
     * 请求指向的目标租户主键
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 请求的资源类型
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 请求的资源业务编码
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 请求的动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 规范化请求内容摘要
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 调用时已发布授权和目标身份的不可变权限快照
     */
    @TableField("permission_snapshot_json")
    private String permissionSnapshotJson;

    /**
     * 调用处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 业务响应编码
     */
    @TableField("response_code")
    private String responseCode;

    /**
     * 允许审计读取的业务响应结果
     */
    @TableField("response_json")
    private String responseJson;

    /**
     * 调用产生或访问的目标业务对象引用
     */
    @TableField("target_reference")
    private String targetReference;

    /**
     * 相同幂等请求复用结果的次数
     */
    @TableField("replay_count")
    private Integer replayCount;

    /**
     * 请求处理耗时毫秒数
     */
    @TableField("duration_millis")
    private Long durationMillis;

    /**
     * 不含敏感信息的错误摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 开始调用时间
     */
    @TableField("call_time")
    private LocalDateTime callTime;

    /**
     * 完成调用时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public Long getApplicationVersionId() {
        return applicationVersionId;
    }

    public void setApplicationVersionId(Long applicationVersionId) {
        this.applicationVersionId = applicationVersionId;
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

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
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

    public LocalDateTime getCallTime() {
        return callTime;
    }

    public void setCallTime(LocalDateTime callTime) {
        this.callTime = callTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    @Override
    public String toString() {
        return "AppCall{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", applicationVersionId = " + applicationVersionId +
            ", credentialVersion = " + credentialVersion +
            ", grantId = " + grantId +
            ", requestId = " + requestId +
            ", traceId = " + traceId +
            ", requestTime = " + requestTime +
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
            ", callTime = " + callTime +
            ", finishTime = " + finishTime +
            "}";
    }
}
