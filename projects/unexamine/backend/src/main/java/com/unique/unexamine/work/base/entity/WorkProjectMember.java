package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("work_project_member")
public class WorkProjectMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("account_id")
    private Long accountId;

    @TableField("project_role")
    private String projectRole;

    @TableField("`status`")
    private String status;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(String projectRole) {
        this.projectRole = projectRole;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public String toString() {
        return "WorkProjectMember{" +
            "id = " + id +
            ", projectId = " + projectId +
            ", accountId = " + accountId +
            ", projectRole = " + projectRole +
            ", status = " + status +
            ", joinedAt = " + joinedAt +
            "}";
    }
}
