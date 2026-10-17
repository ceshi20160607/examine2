package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户成员角色关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_member_role")
public class SystemMemberRole {

    /**
     * 租户成员角色关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 获授权的租户成员标识
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 授予的租户角色标识
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 租户成员角色关系创建时间
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

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
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
        return "SystemMemberRole{" +
            "id = " + id +
            ", tenantId = " + tenantId +
            ", tenantMemberId = " + tenantMemberId +
            ", roleId = " + roleId +
            ", createTime = " + createTime +
            "}";
    }
}
