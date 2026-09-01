package com.unique.unexamine.work.base.entity;

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
@TableName("work_task_history")
public class WorkTaskHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("action_code")
    private String actionCode;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    @TableField("comment_text")
    private String commentText;

    @TableField("changed_by_account_id")
    private Long changedByAccountId;

    @TableField("changed_at")
    private LocalDateTime changedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public Long getChangedByAccountId() {
        return changedByAccountId;
    }

    public void setChangedByAccountId(Long changedByAccountId) {
        this.changedByAccountId = changedByAccountId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "WorkTaskHistory{" +
            "id = " + id +
            ", taskId = " + taskId +
            ", actionCode = " + actionCode +
            ", beforeJson = " + beforeJson +
            ", afterJson = " + afterJson +
            ", commentText = " + commentText +
            ", changedByAccountId = " + changedByAccountId +
            ", changedAt = " + changedAt +
            "}";
    }
}
