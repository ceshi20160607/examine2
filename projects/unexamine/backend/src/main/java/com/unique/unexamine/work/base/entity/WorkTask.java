package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_task")
public class WorkTask {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务上下文类型：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统标识，平台范围为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识，平台范围为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属项目标识
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 所属项目任务组标识
     */
    @TableField("task_group_id")
    private Long taskGroupId;

    /**
     * 父任务标识
     */
    @TableField("parent_task_id")
    private Long parentTaskId;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 业务说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 任务类型：普通或项目任务
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 任务优先级
     */
    @TableField("priority")
    private String priority;

    /**
     * 业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 完成百分比
     */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

    /**
     * 预计工时
     */
    @TableField("estimated_hours")
    private BigDecimal estimatedHours;

    /**
     * 实际工时
     */
    @TableField("actual_hours")
    private BigDecimal actualHours;

    /**
     * 任务标签列表
     */
    @TableField("labels_json")
    private String labelsJson;

    /**
     * 任务检查清单
     */
    @TableField("checklist_json")
    private String checklistJson;

    /**
     * 负责人平台账号标识
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 任务负责人租户成员ID
     */
    @TableField("owner_tenant_member_id")
    private Long ownerTenantMemberId;

    /**
     * 任务开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 任务截止时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 任务完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 完成说明
     */
    @TableField("completion_note")
    private String completionNote;

    /**
     * 提交完成时间
     */
    @TableField("completion_submitted_time")
    private LocalDateTime completionSubmittedTime;

    /**
     * 验收通过时间
     */
    @TableField("completion_accepted_time")
    private LocalDateTime completionAcceptedTime;

    /**
     * 归档时间
     */
    @TableField("archive_time")
    private LocalDateTime archiveTime;

    /**
     * 任务删除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /**
     * 可配置字段值
     */
    @TableField("custom_values_json")
    private String customValuesJson;

    /**
     * 关联业务对象类型
     */
    @TableField("business_type")
    private String businessType;

    /**
     * 关联业务对象标识
     */
    @TableField("business_id")
    private String businessId;

    /**
     * 关联业务对象标题快照
     */
    @TableField("business_title")
    private String businessTitle;

    /**
     * 创建人平台账号标识
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最后修改时间
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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getTaskGroupId() {
        return taskGroupId;
    }

    public void setTaskGroupId(Long taskGroupId) {
        this.taskGroupId = taskGroupId;
    }

    public Long getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public void setActualHours(BigDecimal actualHours) {
        this.actualHours = actualHours;
    }

    public String getLabelsJson() {
        return labelsJson;
    }

    public void setLabelsJson(String labelsJson) {
        this.labelsJson = labelsJson;
    }

    public String getChecklistJson() {
        return checklistJson;
    }

    public void setChecklistJson(String checklistJson) {
        this.checklistJson = checklistJson;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getOwnerTenantMemberId() {
        return ownerTenantMemberId;
    }

    public void setOwnerTenantMemberId(Long ownerTenantMemberId) {
        this.ownerTenantMemberId = ownerTenantMemberId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }

    public String getCompletionNote() {
        return completionNote;
    }

    public void setCompletionNote(String completionNote) {
        this.completionNote = completionNote;
    }

    public LocalDateTime getCompletionSubmittedTime() {
        return completionSubmittedTime;
    }

    public void setCompletionSubmittedTime(LocalDateTime completionSubmittedTime) {
        this.completionSubmittedTime = completionSubmittedTime;
    }

    public LocalDateTime getCompletionAcceptedTime() {
        return completionAcceptedTime;
    }

    public void setCompletionAcceptedTime(LocalDateTime completionAcceptedTime) {
        this.completionAcceptedTime = completionAcceptedTime;
    }

    public LocalDateTime getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(LocalDateTime archiveTime) {
        this.archiveTime = archiveTime;
    }

    public LocalDateTime getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(LocalDateTime deleteTime) {
        this.deleteTime = deleteTime;
    }

    public String getCustomValuesJson() {
        return customValuesJson;
    }

    public void setCustomValuesJson(String customValuesJson) {
        this.customValuesJson = customValuesJson;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getBusinessTitle() {
        return businessTitle;
    }

    public void setBusinessTitle(String businessTitle) {
        this.businessTitle = businessTitle;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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
        return "WorkTask{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", projectId = " + projectId +
            ", taskGroupId = " + taskGroupId +
            ", parentTaskId = " + parentTaskId +
            ", title = " + title +
            ", description = " + description +
            ", taskType = " + taskType +
            ", priority = " + priority +
            ", status = " + status +
            ", progressPercent = " + progressPercent +
            ", estimatedHours = " + estimatedHours +
            ", actualHours = " + actualHours +
            ", labelsJson = " + labelsJson +
            ", checklistJson = " + checklistJson +
            ", ownerAccountId = " + ownerAccountId +
            ", ownerTenantMemberId = " + ownerTenantMemberId +
            ", startTime = " + startTime +
            ", dueTime = " + dueTime +
            ", completeTime = " + completeTime +
            ", completionNote = " + completionNote +
            ", completionSubmittedTime = " + completionSubmittedTime +
            ", completionAcceptedTime = " + completionAcceptedTime +
            ", archiveTime = " + archiveTime +
            ", deleteTime = " + deleteTime +
            ", customValuesJson = " + customValuesJson +
            ", businessType = " + businessType +
            ", businessId = " + businessId +
            ", businessTitle = " + businessTitle +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
