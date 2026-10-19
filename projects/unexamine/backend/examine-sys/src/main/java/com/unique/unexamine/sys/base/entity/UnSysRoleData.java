package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统角色数据权限
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_sys_role_data")
public class UnSysRoleData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户角色数据权限主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 数据范围：1本人、2本人及下属、3本部门、4本部门及下级、5全部、6指定部门
     */
    @TableField("scope_type")
    private Byte scopeType;

    /**
     * 指定租户部门主键，非指定部门范围时为0
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 授权时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Byte getScopeType() {
        return scopeType;
    }

    public void setScopeType(Byte scopeType) {
        this.scopeType = scopeType;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
