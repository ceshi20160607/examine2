package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 租户成员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_sys_tenant_member")
public class UnSysTenantMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户成员主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 关联的系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 成员所属的租户部门主键
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 租户内成员编号
     */
    @TableField("member_no")
    private String memberNo;

    /**
     * 成员在该租户的岗位名称
     */
    @TableField("position_name")
    private String positionName;

    /**
     * 直属上级租户成员主键
     */
    @TableField("leader_tenant_member_id")
    private Long leaderTenantMemberId;

    /**
     * 租户成员状态：1在职、0停用
     */
    @TableField("status")
    private Byte status;

    /**
     * 加入租户时间
     */
    @TableField("join_time")
    private LocalDateTime joinTime;

    /**
     * 关系创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 关系更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getMemberNo() {
        return memberNo;
    }

    public void setMemberNo(String memberNo) {
        this.memberNo = memberNo;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public Long getLeaderTenantMemberId() {
        return leaderTenantMemberId;
    }

    public void setLeaderTenantMemberId(Long leaderTenantMemberId) {
        this.leaderTenantMemberId = leaderTenantMemberId;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(LocalDateTime joinTime) {
        this.joinTime = joinTime;
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
