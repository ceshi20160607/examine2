package com.unique.unexamine.audit.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变的业务操作审计事件
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_audit_event")
public class AuditEvent {

    /**
     * 操作审计事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 跨服务调用链追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 触发操作的请求标识
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 事件所属的平台或系统范围类型
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 执行操作的平台账号主键，系统事件也使用统一账号
     */
    @TableField("actor_account_id")
    private Long actorAccountId;

    /**
     * 事件所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 事件所属系统主键，平台事件为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 事件所属租户主键，平台或系统公共事件为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 执行操作的系统成员主键，平台事件为空
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 应用调用产生事件时的应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 审计事件业务类别
     */
    @TableField("event_category")
    private String eventCategory;

    /**
     * 审计事件动作编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 被操作业务对象类型
     */
    @TableField("object_type")
    private String objectType;

    /**
     * 被操作业务对象标识
     */
    @TableField("object_id")
    private String objectId;

    /**
     * 操作结果或错误编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 发起操作的客户端 IP 地址
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 发起操作的客户端标识
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 执行时的权限判定快照
     */
    @TableField("permission_snapshot")
    private String permissionSnapshot;

    /**
     * 不含敏感明文的审计事件详情
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 审计事件发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

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

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
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
            ", occurTime = " + occurTime +
            "}";
    }
}
