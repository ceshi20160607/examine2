package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 任务状态与业务操作历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_task_history")
public class WorkTaskHistory {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务标识
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 任务操作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 操作前任务快照
     */
    @TableField("before_json")
    private String beforeJson;

    /**
     * 操作后任务快照
     */
    @TableField("after_json")
    private String afterJson;

    /**
     * 操作补充说明
     */
    @TableField("comment_text")
    private String commentText;

    /**
     * 操作人平台账号标识
     */
    @TableField("changed_by_account_id")
    private Long changedByAccountId;

    /**
     * 任务操作时间
     */
    @TableField("change_time")
    private LocalDateTime changeTime;

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

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
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
            ", changeTime = " + changeTime +
            "}";
    }
}
