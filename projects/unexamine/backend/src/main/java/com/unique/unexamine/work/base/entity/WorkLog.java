package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("work_log")
public class WorkLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_type")
    private String contextType;

    @TableField("platform_id")
    private Long platformId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("project_id")
    private Long projectId;

    @TableField("author_account_id")
    private Long authorAccountId;

    @TableField("author_tenant_member_id")
    private Long authorTenantMemberId;

    @TableField("work_date")
    private LocalDate workDate;

    @TableField("title")
    private String title;

    @TableField("content_text")
    private String contentText;

    /**
     * 今日已完成事项
     */
    @TableField("completed_text")
    private String completedText;

    /**
     * 当前进行中事项
     */
    @TableField("in_progress_text")
    private String inProgressText;

    /**
     * 下一工作日计划
     */
    @TableField("next_plan_text")
    private String nextPlanText;

    /**
     * 风险与阻塞说明
     */
    @TableField("risk_text")
    private String riskText;

    /**
     * 需要协助的事项
     */
    @TableField("assistance_text")
    private String assistanceText;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("`status`")
    private String status;

    /**
     * 可见范围：本人、直属管理、项目成员、同部门或指定角色
     */
    @TableField("visibility_type")
    private String visibilityType;

    /**
     * 指定可见角色标识快照
     */
    @TableField("visible_role_ids_json")
    private String visibleRoleIdsJson;

    /**
     * 最近一次提交时间
     */
    @TableField("submitted_time")
    private LocalDateTime submittedTime;

    /**
     * 最近一次撤回时间
     */
    @TableField("withdrawn_time")
    private LocalDateTime withdrawnTime;

    @TableField("custom_values_json")
    private String customValuesJson;

    @TableField("business_type")
    private String businessType;

    @TableField("business_id")
    private String businessId;

    @TableField("business_title")
    private String businessTitle;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public Long getAuthorAccountId() {
        return authorAccountId;
    }

    public void setAuthorAccountId(Long authorAccountId) {
        this.authorAccountId = authorAccountId;
    }

    public Long getAuthorTenantMemberId() {
        return authorTenantMemberId;
    }

    public void setAuthorTenantMemberId(Long authorTenantMemberId) {
        this.authorTenantMemberId = authorTenantMemberId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getCompletedText() {
        return completedText;
    }

    public void setCompletedText(String completedText) {
        this.completedText = completedText;
    }

    public String getInProgressText() {
        return inProgressText;
    }

    public void setInProgressText(String inProgressText) {
        this.inProgressText = inProgressText;
    }

    public String getNextPlanText() {
        return nextPlanText;
    }

    public void setNextPlanText(String nextPlanText) {
        this.nextPlanText = nextPlanText;
    }

    public String getRiskText() {
        return riskText;
    }

    public void setRiskText(String riskText) {
        this.riskText = riskText;
    }

    public String getAssistanceText() {
        return assistanceText;
    }

    public void setAssistanceText(String assistanceText) {
        this.assistanceText = assistanceText;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(String visibilityType) {
        this.visibilityType = visibilityType;
    }

    public String getVisibleRoleIdsJson() {
        return visibleRoleIdsJson;
    }

    public void setVisibleRoleIdsJson(String visibleRoleIdsJson) {
        this.visibleRoleIdsJson = visibleRoleIdsJson;
    }

    public LocalDateTime getSubmittedTime() {
        return submittedTime;
    }

    public void setSubmittedTime(LocalDateTime submittedTime) {
        this.submittedTime = submittedTime;
    }

    public LocalDateTime getWithdrawnTime() {
        return withdrawnTime;
    }

    public void setWithdrawnTime(LocalDateTime withdrawnTime) {
        this.withdrawnTime = withdrawnTime;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "WorkLog{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", projectId = " + projectId +
            ", authorAccountId = " + authorAccountId +
            ", authorTenantMemberId = " + authorTenantMemberId +
            ", workDate = " + workDate +
            ", title = " + title +
            ", contentText = " + contentText +
            ", completedText = " + completedText +
            ", inProgressText = " + inProgressText +
            ", nextPlanText = " + nextPlanText +
            ", riskText = " + riskText +
            ", assistanceText = " + assistanceText +
            ", durationMinutes = " + durationMinutes +
            ", status = " + status +
            ", visibilityType = " + visibilityType +
            ", visibleRoleIdsJson = " + visibleRoleIdsJson +
            ", submittedTime = " + submittedTime +
            ", withdrawnTime = " + withdrawnTime +
            ", customValuesJson = " + customValuesJson +
            ", businessType = " + businessType +
            ", businessId = " + businessId +
            ", businessTitle = " + businessTitle +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
