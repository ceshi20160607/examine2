package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 普通任务及项目任务
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_work_task")
public class UnWorkTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统任务作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统任务所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统任务所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 项目任务所属项目主键，普通任务为空
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 项目任务所属任务组主键
     */
    @TableField("task_group_id")
    private Long taskGroupId;

    /**
     * 子任务的上级任务主键
     */
    @TableField("parent_task_id")
    private Long parentTaskId;

    /**
     * 任务标题
     */
    @TableField("title")
    private String title;

    /**
     * 任务详细内容
     */
    @TableField("description")
    private String description;

    /**
     * 当前负责人平台账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 创建任务的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

    /**
     * 优先级字典项标识
     */
    @TableField("priority_code")
    private String priorityCode;

    /**
     * 未开始进行阻塞待确认完成取消等状态
     */
    @TableField("status")
    private String status;

    /**
     * 任务完成进度百分比
     */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

    /**
     * 预计工作小时数
     */
    @TableField("expected_hours")
    private BigDecimal expectedHours;

    /**
     * 实际工作小时数
     */
    @TableField("actual_hours")
    private BigDecimal actualHours;

    /**
     * 任务计划截止时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 任务实际完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 关联业务模块主键
     */
    @TableField("related_module_id")
    private Long relatedModuleId;

    /**
     * 关联业务记录主键
     */
    @TableField("related_record_id")
    private Long relatedRecordId;

    /**
     * 任务引用的标签字典项主键集合
     */
    @TableField("tag_json")
    private String tagJson;

    /**
     * 已发布任务字段配置对应的值
     */
    @TableField("field_values_json")
    private String fieldValuesJson;

    /**
     * 任务归档时间
     */
    @TableField("archived_time")
    private LocalDateTime archivedTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
    }

    public String getPriorityCode() {
        return priorityCode;
    }

    public void setPriorityCode(String priorityCode) {
        this.priorityCode = priorityCode;
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

    public BigDecimal getExpectedHours() {
        return expectedHours;
    }

    public void setExpectedHours(BigDecimal expectedHours) {
        this.expectedHours = expectedHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public void setActualHours(BigDecimal actualHours) {
        this.actualHours = actualHours;
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

    public Long getRelatedModuleId() {
        return relatedModuleId;
    }

    public void setRelatedModuleId(Long relatedModuleId) {
        this.relatedModuleId = relatedModuleId;
    }

    public Long getRelatedRecordId() {
        return relatedRecordId;
    }

    public void setRelatedRecordId(Long relatedRecordId) {
        this.relatedRecordId = relatedRecordId;
    }

    public String getTagJson() {
        return tagJson;
    }

    public void setTagJson(String tagJson) {
        this.tagJson = tagJson;
    }

    public String getFieldValuesJson() {
        return fieldValuesJson;
    }

    public void setFieldValuesJson(String fieldValuesJson) {
        this.fieldValuesJson = fieldValuesJson;
    }

    public LocalDateTime getArchivedTime() {
        return archivedTime;
    }

    public void setArchivedTime(LocalDateTime archivedTime) {
        this.archivedTime = archivedTime;
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
}
