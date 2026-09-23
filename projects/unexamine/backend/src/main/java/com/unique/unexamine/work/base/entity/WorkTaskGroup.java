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
 * 项目任务组
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("work_task_group")
public class WorkTaskGroup {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("`name`")
    private String name;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("`status`")
    private String status;

    /**
     * 任务组负责人账号标识
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 任务组负责人租户成员标识
     */
    @TableField("owner_tenant_member_id")
    private Long ownerTenantMemberId;

    /**
     * 任务组截止时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 任务组完成百分比
     */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
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
        return "WorkTaskGroup{" +
            "id = " + id +
            ", projectId = " + projectId +
            ", name = " + name +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", ownerAccountId = " + ownerAccountId +
            ", ownerTenantMemberId = " + ownerTenantMemberId +
            ", dueTime = " + dueTime +
            ", progressPercent = " + progressPercent +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
