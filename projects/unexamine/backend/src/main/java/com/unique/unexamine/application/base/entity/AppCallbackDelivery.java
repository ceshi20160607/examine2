package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用事件回调投递及重试结果
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_callback_delivery")
public class AppCallbackDelivery {

    /**
     * 回调投递主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属回调配置主键
     */
    @TableField("callback_id")
    private Long callbackId;

    /**
     * 业务事件唯一标识
     */
    @TableField("event_id")
    private String eventId;

    /**
     * 投递内容摘要
     */
    @TableField("payload_hash")
    private String payloadHash;

    /**
     * 投递状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 已尝试投递次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 下次允许重试时间
     */
    @TableField("next_attempt_time")
    private LocalDateTime nextAttemptTime;

    /**
     * 接收端HTTP状态码
     */
    @TableField("response_status")
    private Integer responseStatus;

    /**
     * 接收端响应的脱敏摘要
     */
    @TableField("response_body_excerpt")
    private String responseBodyExcerpt;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本
     */
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

    public LocalDateTime getNextAttemptTime() {
        return nextAttemptTime;
    }

    public void setNextAttemptTime(LocalDateTime nextAttemptTime) {
        this.nextAttemptTime = nextAttemptTime;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", nextAttemptTime = " + nextAttemptTime +
            ", responseStatus = " + responseStatus +
            ", responseBodyExcerpt = " + responseBodyExcerpt +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
