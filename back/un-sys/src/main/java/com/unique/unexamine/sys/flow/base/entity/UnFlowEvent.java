package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 不可变流程事件历史
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_event")
public class UnFlowEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 不可变流程事件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 关联节点待办主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 产生事件的流程操作主键
     */
    @TableField("action_id")
    private Long actionId;

    /**
     * 节点进入审批路由等事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 内部事件实际操作平台账号主键
     */
    @TableField("actor_account_id")
    private Long actorAccountId;

    /**
     * 外部事件实际操作人标识
     */
    @TableField("actor_external_id")
    private String actorExternalId;

    /**
     * 流程版本路径原因字段及权限审计快照
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 事件发生时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(Long actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public String getActorExternalId() {
        return actorExternalId;
    }

    public void setActorExternalId(String actorExternalId) {
        this.actorExternalId = actorExternalId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
