package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变的审批操作历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_event")
public class FlowEvent {

    /**
     * 审批事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 有关联时的待办任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 执行操作的系统成员主键
     */
    @TableField("actor_member_id")
    private Long actorMemberId;

    /**
     * 提交、同意或拒绝事件类型
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 操作意见或拒绝原因
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 事件发生时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public void setActorMemberId(Long actorMemberId) {
        this.actorMemberId = actorMemberId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FlowEvent{" +
            "id = " + id +
            ", exampleId = " + exampleId +
            ", taskId = " + taskId +
            ", actorMemberId = " + actorMemberId +
            ", eventType = " + eventType +
            ", opinion = " + opinion +
            ", createTime = " + createTime +
            "}";
    }
}
