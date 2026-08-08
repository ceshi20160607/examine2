package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_plat_member_manager_assignment")
public class MemberManagerAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableId("assignment_id")
    private Long assignmentId;

    @TableField("member_id")
    private Long memberId;

    @TableField("manager_member_id")
    private Long managerMemberId;

    @TableField("status")
    private String status;

    @TableField("assigned_by")
    private Long assignedBy;

    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    @TableField("cleared_by")
    private Long clearedBy;

    @TableField("cleared_at")
    private LocalDateTime clearedAt;

    @Version
    @TableField("version")
    private Long version;

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

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getManagerMemberId() {
        return managerMemberId;
    }

    public void setManagerMemberId(Long managerMemberId) {
        this.managerMemberId = managerMemberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Long getClearedBy() {
        return clearedBy;
    }

    public void setClearedBy(Long clearedBy) {
        this.clearedBy = clearedBy;
    }

    public LocalDateTime getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(LocalDateTime clearedAt) {
        this.clearedAt = clearedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
