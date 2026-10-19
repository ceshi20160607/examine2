package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台成员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_plat_member")
public class UnPlatMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台成员主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的平台登录账号主键，未开通登录时为空
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 平台成员编号
     */
    @TableField("member_no")
    private String memberNo;

    /**
     * 成员姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 所属平台部门主键
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 直属上级的平台成员主键
     */
    @TableField("leader_member_id")
    private Long leaderMemberId;

    /**
     * 手机号码
     */
    @TableField("mobile")
    private String mobile;

    /**
     * 电子邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 性别：0未设置、1男、2女
     */
    @TableField("gender")
    private Byte gender;

    /**
     * 岗位名称
     */
    @TableField("position_name")
    private String positionName;

    /**
     * 成员状态：1在职、0停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getMemberNo() {
        return memberNo;
    }

    public void setMemberNo(String memberNo) {
        this.memberNo = memberNo;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
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

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Byte getGender() {
        return gender;
    }

    public void setGender(Byte gender) {
        this.gender = gender;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
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
