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
 * @since 2026-10-19
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
     * 参与的系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 项目内角色编码
     */
    @TableField("role_code")
    private String roleCode;

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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(LocalDateTime joinTime) {
        this.joinTime = joinTime;
    }
}
