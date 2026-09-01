package com.unique.unexamine.audit.base.entity;

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
@TableName("audit_event")
public class AuditEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trace_id")
    private String traceId;

    @TableField("request_id")
    private String requestId;

    @TableField("context_type")
    private String contextType;

    @TableField("actor_account_id")
    private Long actorAccountId;

    @TableField("platform_id")
    private Long platformId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("member_id")
    private Long memberId;

    @TableField("application_id")
    private Long applicationId;

    @TableField("event_category")
    private String eventCategory;

    @TableField("event_code")
    private String eventCode;

    @TableField("object_type")
    private String objectType;

    @TableField("object_id")
    private String objectId;

    @TableField("result_code")
    private String resultCode;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("permission_snapshot")
    private String permissionSnapshot;

    @TableField("detail_json")
    private String detailJson;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(Long actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getPermissionSnapshot() {
        return permissionSnapshot;
    }

    public void setPermissionSnapshot(String permissionSnapshot) {
        this.permissionSnapshot = permissionSnapshot;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
            "id = " + id +
            ", traceId = " + traceId +
            ", requestId = " + requestId +
            ", contextType = " + contextType +
            ", actorAccountId = " + actorAccountId +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", memberId = " + memberId +
            ", applicationId = " + applicationId +
            ", eventCategory = " + eventCategory +
            ", eventCode = " + eventCode +
            ", objectType = " + objectType +
            ", objectId = " + objectId +
            ", resultCode = " + resultCode +
            ", clientIp = " + clientIp +
            ", userAgent = " + userAgent +
            ", permissionSnapshot = " + permissionSnapshot +
            ", detailJson = " + detailJson +
            ", occurredAt = " + occurredAt +
            "}";
    }
}
