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
@TableName("flow_action")
public class FlowAction {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("task_id")
    private Long taskId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("action_code")
    private String actionCode;

    @TableField("comment_text")
    private String commentText;

    @TableField("input_json")
    private String inputJson;

    @TableField("result_json")
    private String resultJson;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("acted_by_account_id")
    private Long actedByAccountId;

    @TableField("acted_at")
    private LocalDateTime actedAt;

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

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(LocalDateTime actedAt) {
        this.actedAt = actedAt;
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
            ", actedAt = " + actedAt +
            "}";
    }
}
