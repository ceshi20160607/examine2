package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 不可变流程事件历史
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_event")
public class UnFlowEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 事件关联的待办任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 触发事件的操作主键
     */
    @TableField("action_id")
    private Long actionId;

    /**
     * 事件类型，如 STARTED、TASK_CREATED、TASK_COMPLETED、FINISHED
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 事件发起成员主键，系统事件可为空
     */
    @TableField("actor_member_id")
    private Long actorMemberId;

    /**
     * 事件内容说明
     */
    @TableField("description")
    private String description;

    /**
     * 事件发生时间
     */
    @TableField("event_time")
    private LocalDateTime eventTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getActionId() {
        return actionId;
    }

    public void setActionId(Long actionId) {
        this.actionId = actionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public void setActorMemberId(Long actorMemberId) {
        this.actorMemberId = actorMemberId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}
