package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 工作项目参与人
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_project_member")
public class WorkProjectMember {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属项目标识
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 参与人平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 项目参与人租户成员ID
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 项目成员角色
     */
    @TableField("project_role")
    private String projectRole;

    /**
     * 业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 加入时间
     */
    @TableField("join_time")
    private LocalDateTime joinTime;

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

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
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

    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(LocalDateTime joinTime) {
        this.joinTime = joinTime;
    }

    @Override
    public String toString() {
        return "WorkProjectMember{" +
            "id = " + id +
            ", projectId = " + projectId +
            ", accountId = " + accountId +
            ", tenantMemberId = " + tenantMemberId +
            ", projectRole = " + projectRole +
            ", status = " + status +
            ", joinTime = " + joinTime +
            "}";
    }
}
