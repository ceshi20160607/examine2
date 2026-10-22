package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作项目参与人
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_work_project_member")
public class UnWorkProjectMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 项目参与人关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作项目主键
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 参与平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 负责人协作或观察成员身份
     */
    @TableField("member_type")
    private String memberType;

    /**
     * 成员关系状态
     */
    @TableField("status")
    private String status;

    /**
     * 加入项目时间
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

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
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
}
