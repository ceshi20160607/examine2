package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志评论与管理反馈
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_log_comment")
public class WorkLogComment {

    /**
     * 日志沟通记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工作日志主键
     */
    @TableField("work_log_id")
    private Long workLogId;

    /**
     * 沟通类型：评论或反馈
     */
    @TableField("comment_type")
    private String commentType;

    /**
     * 评论或反馈正文
     */
    @TableField("comment_text")
    private String commentText;

    /**
     * 发表人的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 发表时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkLogId() {
        return workLogId;
    }

    public void setWorkLogId(Long workLogId) {
        this.workLogId = workLogId;
    }

    public String getCommentType() {
        return commentType;
    }

    public void setCommentType(String commentType) {
        this.commentType = commentType;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "WorkLogComment{" +
            "id = " + id +
            ", workLogId = " + workLogId +
            ", commentType = " + commentType +
            ", commentText = " + commentText +
            ", createdByAccountId = " + createdByAccountId +
            ", createdTime = " + createdTime +
            "}";
    }
}
