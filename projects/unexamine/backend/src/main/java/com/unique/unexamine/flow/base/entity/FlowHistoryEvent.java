package com.unique.unexamine.flow.base.entity;

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
@TableName("flow_history_event")
public class FlowHistoryEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("execution_id")
    private Long executionId;

    @TableField("task_id")
    private Long taskId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("event_type")
    private String eventType;

    @TableField("event_name")
    private String eventName;

    @TableField("actor_account_id")
    private Long actorAccountId;

    @TableField("actor_tenant_member_id")
    private Long actorTenantMemberId;

    @TableField("before_status")
    private String beforeStatus;

    @TableField("after_status")
    private String afterStatus;

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

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(Long actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public Long getActorTenantMemberId() {
        return actorTenantMemberId;
    }

    public void setActorTenantMemberId(Long actorTenantMemberId) {
        this.actorTenantMemberId = actorTenantMemberId;
    }

    public String getBeforeStatus() {
        return beforeStatus;
    }

    public void setBeforeStatus(String beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public String getAfterStatus() {
        return afterStatus;
    }

    public void setAfterStatus(String afterStatus) {
        this.afterStatus = afterStatus;
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
        return "FlowHistoryEvent{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", executionId = " + executionId +
            ", taskId = " + taskId +
            ", nodeKey = " + nodeKey +
            ", eventType = " + eventType +
            ", eventName = " + eventName +
            ", actorAccountId = " + actorAccountId +
            ", actorTenantMemberId = " + actorTenantMemberId +
            ", beforeStatus = " + beforeStatus +
            ", afterStatus = " + afterStatus +
            ", detailJson = " + detailJson +
            ", occurredAt = " + occurredAt +
            "}";
    }
}
