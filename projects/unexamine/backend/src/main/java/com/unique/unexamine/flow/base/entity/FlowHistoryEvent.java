package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变流程事件历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_history_event")
public class FlowHistoryEvent {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例ID
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 关联节点执行轮次ID
     */
    @TableField("execution_id")
    private Long executionId;

    /**
     * 所属流程待办任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 历史事件类型
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 历史事件显示名称
     */
    @TableField("event_name")
    private String eventName;

    /**
     * 事件操作者平台账号ID
     */
    @TableField("actor_account_id")
    private Long actorAccountId;

    /**
     * 事件操作者租户成员ID
     */
    @TableField("actor_tenant_member_id")
    private Long actorTenantMemberId;

    /**
     * 事件发生前状态
     */
    @TableField("before_status")
    private String beforeStatus;

    /**
     * 事件发生后状态
     */
    @TableField("after_status")
    private String afterStatus;

    /**
     * 事件业务详情快照
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 事件或异常发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

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

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
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
            ", occurTime = " + occurTime +
            "}";
    }
}
