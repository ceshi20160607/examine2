package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台自身业务的待办投影
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_todo_item")
public class PlatTodoItem {

    /**
     * 平台待办主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 待办所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 待办接收平台成员账号主键
     */
    @TableField("assignee_account_id")
    private Long assigneeAccountId;

    /**
     * 平台待办类别
     */
    @TableField("todo_type")
    private String todoType;

    /**
     * 平台来源业务类别
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 平台来源业务标识
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 待办标题快照
     */
    @TableField("title")
    private String title;

    /**
     * 待办摘要快照
     */
    @TableField("summary")
    private String summary;

    /**
     * 平台路由或系统切换入口，不得直达系统业务详情
     */
    @TableField("target_route")
    private String targetRoute;

    /**
     * 待办优先级
     */
    @TableField("priority")
    private String priority;

    /**
     * 待办截止时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 平台待办投影状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 待办处理完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 待办创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 待办更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public void setAssigneeAccountId(Long assigneeAccountId) {
        this.assigneeAccountId = assigneeAccountId;
    }

    public String getTodoType() {
        return todoType;
    }

    public void setTodoType(String todoType) {
        this.todoType = todoType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public void setTargetRoute(String targetRoute) {
        this.targetRoute = targetRoute;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "PlatTodoItem{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", assigneeAccountId = " + assigneeAccountId +
            ", todoType = " + todoType +
            ", sourceType = " + sourceType +
            ", sourceId = " + sourceId +
            ", title = " + title +
            ", summary = " + summary +
            ", targetRoute = " + targetRoute +
            ", priority = " + priority +
            ", dueTime = " + dueTime +
            ", status = " + status +
            ", completeTime = " + completeTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
