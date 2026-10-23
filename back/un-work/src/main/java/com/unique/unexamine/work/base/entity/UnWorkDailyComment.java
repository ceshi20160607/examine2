package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志评论与管理反馈
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_work_daily_comment")
public class UnWorkDailyComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作日志评论主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被评论工作日志的首版主键
     */
    @TableField("daily_root_id")
    private Long dailyRootId;

    /**
     * 回复的上级评论主键
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 评论平台账号主键
     */
    @TableField("operator_account_id")
    private Long operatorAccountId;

    /**
     * 评论或管理反馈内容
     */
    @TableField("content")
    private String content;

    /**
     * 正常或撤回状态
     */
    @TableField("status")
    private String status;

    /**
     * 评论时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getDailyRootId() {
        return dailyRootId;
    }

    public void setDailyRootId(Long dailyRootId) {
        this.dailyRootId = dailyRootId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getOperatorAccountId() {
        return operatorAccountId;
    }

    public void setOperatorAccountId(Long operatorAccountId) {
        this.operatorAccountId = operatorAccountId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
