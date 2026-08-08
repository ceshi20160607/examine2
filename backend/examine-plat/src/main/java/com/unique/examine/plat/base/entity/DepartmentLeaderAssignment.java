package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_plat_department_leader_assignment")
public class DepartmentLeaderAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableId("assignment_id")
    private Long assignmentId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("leader_member_id")
    private Long leaderMemberId;

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

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getLeaderMemberId() {
        return leaderMemberId;
    }

    public void setLeaderMemberId(Long leaderMemberId) {
        this.leaderMemberId = leaderMemberId;
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
