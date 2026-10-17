package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 待投递的本地业务事件及投递结果
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_event_outbox")
public class CoreEventOutbox {

    /**
     * 待投递业务事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 事件所属的平台或系统范围类型
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 系统事件所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户事件所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 产生事件的业务聚合类型
     */
    @TableField("aggregate_type")
    private String aggregateType;

    /**
     * 产生事件的业务聚合标识
     */
    @TableField("aggregate_id")
    private String aggregateId;

    /**
     * 需要投递的业务事件类型
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 待投递事件的业务载荷
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 事件投递状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 事件允许开始投递的时间
     */
    @TableField("available_time")
    private LocalDateTime availableTime;

    /**
     * 事件成功投递完成时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 事件投递失败后的重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 最近一次投递失败原因
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 事件进入待投递箱的时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 待投递事件最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 待投递事件乐观锁版本
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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
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

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAvailableTime() {
        return availableTime;
    }

    public void setAvailableTime(LocalDateTime availableTime) {
        this.availableTime = availableTime;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
        return "CoreEventOutbox{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", aggregateType = " + aggregateType +
            ", aggregateId = " + aggregateId +
            ", eventType = " + eventType +
            ", payloadJson = " + payloadJson +
            ", status = " + status +
            ", availableTime = " + availableTime +
            ", publishTime = " + publishTime +
            ", retryCount = " + retryCount +
            ", lastError = " + lastError +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
