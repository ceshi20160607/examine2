package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程操作及幂等记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_action")
public class FlowAction {

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
     * 流程操作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 操作意见正文
     */
    @TableField("comment_text")
    private String commentText;

    /**
     * 本次操作输入快照
     */
    @TableField("input_json")
    private String inputJson;

    /**
     * 本次操作结果快照
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 操作幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 操作人平台账号ID
     */
    @TableField("acted_by_account_id")
    private Long actedByAccountId;

    /**
     * 操作人在实际运行租户的成员ID
     */
    @TableField("acted_by_tenant_member_id")
    private Long actedByTenantMemberId;

    /**
     * 外部操作来源应用ID；内部操作为空
     */
    @TableField("actor_application_id")
    private Long actorApplicationId;

    /**
     * 应用命名空间内的外部操作人标识
     */
    @TableField("actor_external_principal_id")
    private String actorExternalPrincipalId;

    /**
     * 操作发生时间
     */
    @TableField("act_time")
    private LocalDateTime actTime;

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

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getActedByAccountId() {
        return actedByAccountId;
    }

    public void setActedByAccountId(Long actedByAccountId) {
        this.actedByAccountId = actedByAccountId;
    }

    public Long getActedByTenantMemberId() {
        return actedByTenantMemberId;
    }

    public void setActedByTenantMemberId(Long actedByTenantMemberId) {
        this.actedByTenantMemberId = actedByTenantMemberId;
    }

    public Long getActorApplicationId() {
        return actorApplicationId;
    }

    public void setActorApplicationId(Long actorApplicationId) {
        this.actorApplicationId = actorApplicationId;
    }

    public String getActorExternalPrincipalId() {
        return actorExternalPrincipalId;
    }

    public void setActorExternalPrincipalId(String actorExternalPrincipalId) {
        this.actorExternalPrincipalId = actorExternalPrincipalId;
    }

    public LocalDateTime getActTime() {
        return actTime;
    }

    public void setActTime(LocalDateTime actTime) {
        this.actTime = actTime;
    }

    @Override
    public String toString() {
        return "FlowAction{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", taskId = " + taskId +
            ", nodeKey = " + nodeKey +
            ", actionCode = " + actionCode +
            ", commentText = " + commentText +
            ", inputJson = " + inputJson +
            ", resultJson = " + resultJson +
            ", idempotencyKey = " + idempotencyKey +
            ", actedByAccountId = " + actedByAccountId +
            ", actedByTenantMemberId = " + actedByTenantMemberId +
            ", actorApplicationId = " + actorApplicationId +
            ", actorExternalPrincipalId = " + actorExternalPrincipalId +
            ", actTime = " + actTime +
            "}";
    }
}
