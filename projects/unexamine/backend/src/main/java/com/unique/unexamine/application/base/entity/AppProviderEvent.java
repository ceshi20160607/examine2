package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台回调事件
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_provider_event")
public class AppProviderEvent {

    /**
     * 外部平台回调事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属外部平台连接主键
     */
    @TableField("connection_id")
    private Long connectionId;

    /**
     * 平台事件幂等标识或服务端稳定摘要
     */
    @TableField("provider_event_id")
    private String providerEventId;

    /**
     * 规范化平台事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 平台侧发送人标识
     */
    @TableField("sender_external_id")
    private String senderExternalId;

    /**
     * 解密后原始载荷SHA-256摘要
     */
    @TableField("payload_hash")
    private String payloadHash;

    /**
     * 处理状态：RECEIVED已接收、PROCESSED已完成、IGNORED无路由、FAILED失败
     */
    @TableField("`status`")
    private String status;

    /**
     * 进入应用网关后生成的调用主键
     */
    @TableField("application_call_id")
    private Long applicationCallId;

    /**
     * 安全的处理结果编码
     */
    @TableField("response_code")
    private String responseCode;

    /**
     * 接收时间
     */
    @TableField("received_time")
    private LocalDateTime receivedTime;

    /**
     * 处理完成时间
     */
    @TableField("processed_time")
    private LocalDateTime processedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getSenderExternalId() {
        return senderExternalId;
    }

    public void setSenderExternalId(String senderExternalId) {
        this.senderExternalId = senderExternalId;
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

    public Long getApplicationCallId() {
        return applicationCallId;
    }

    public void setApplicationCallId(Long applicationCallId) {
        this.applicationCallId = applicationCallId;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(LocalDateTime receivedTime) {
        this.receivedTime = receivedTime;
    }

    public LocalDateTime getProcessedTime() {
        return processedTime;
    }

    public void setProcessedTime(LocalDateTime processedTime) {
        this.processedTime = processedTime;
    }

    @Override
    public String toString() {
        return "AppProviderEvent{" +
            "id = " + id +
            ", connectionId = " + connectionId +
            ", providerEventId = " + providerEventId +
            ", eventCode = " + eventCode +
            ", senderExternalId = " + senderExternalId +
            ", payloadHash = " + payloadHash +
            ", status = " + status +
            ", applicationCallId = " + applicationCallId +
            ", responseCode = " + responseCode +
            ", receivedTime = " + receivedTime +
            ", processedTime = " + processedTime +
            "}";
    }
}
