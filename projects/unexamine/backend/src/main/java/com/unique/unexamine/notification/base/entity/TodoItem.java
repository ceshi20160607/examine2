package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户内可处理待办投影
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_todo_item")
public class TodoItem {

    /**
     * 系统待办主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 固定为系统上下文
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 系统所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 待办所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 待办所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 待办接收账号主键
     */
    @TableField("assignee_account_id")
    private Long assigneeAccountId;

    /**
     * 接收人在当前系统中的成员主键
     */
    @TableField("assignee_member_id")
    private Long assigneeMemberId;

    /**
     * 待办类别，例如审批或工作任务
     */
    @TableField("todo_type")
    private String todoType;

    /**
     * 来源业务对象类别
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源业务对象标识，处理时须重新校验来源状态
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
     * 系统范围内的跳转路由，打开前须切换并验证权限
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
     * 待办投影状态，不替代来源状态
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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public void setAssigneeAccountId(Long assigneeAccountId) {
        this.assigneeAccountId = assigneeAccountId;
    }

    public Long getAssigneeMemberId() {
        return assigneeMemberId;
    }

    public void setAssigneeMemberId(Long assigneeMemberId) {
        this.assigneeMemberId = assigneeMemberId;
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
        return "TodoItem{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", assigneeAccountId = " + assigneeAccountId +
            ", assigneeMemberId = " + assigneeMemberId +
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
