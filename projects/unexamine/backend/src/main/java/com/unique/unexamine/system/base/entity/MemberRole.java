package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统成员与租户角色关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_member_role")
public class MemberRole {

    /**
     * 成员角色关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 授权所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 被授权系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 授予的租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 角色授权时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "MemberRole{" +
            "id = " + id +
            ", tenantId = " + tenantId +
            ", memberId = " + memberId +
            ", roleId = " + roleId +
            ", createTime = " + createTime +
            "}";
    }
}
