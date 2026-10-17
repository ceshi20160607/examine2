package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户角色对模块菜单和基础记录动作的授权
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_role_menu")
public class RoleMenuGrant {

    /**
     * 角色菜单授权主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被授权租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 被授权模块菜单主键
     */
    @TableField("menu_id")
    private Long menuId;

    /**
     * 是否允许查看菜单和记录
     */
    @TableField("can_view")
    private Boolean canView;

    /**
     * 是否允许新增记录
     */
    @TableField("can_create")
    private Boolean canCreate;

    /**
     * 是否允许编辑记录
     */
    @TableField("can_update")
    private Boolean canUpdate;

    /**
     * 是否允许删除记录
     */
    @TableField("can_delete")
    private Boolean canDelete;

    /**
     * 是否允许导出当前有权查看的模块记录
     */
    @TableField("can_export")
    private Boolean canExport;

    /**
     * 角色菜单授权创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 角色菜单授权更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public Boolean getCanView() {
        return canView;
    }

    public void setCanView(Boolean canView) {
        this.canView = canView;
    }

    public Boolean getCanCreate() {
        return canCreate;
    }

    public void setCanCreate(Boolean canCreate) {
        this.canCreate = canCreate;
    }

    public Boolean getCanUpdate() {
        return canUpdate;
    }

    public void setCanUpdate(Boolean canUpdate) {
        this.canUpdate = canUpdate;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }

    public Boolean getCanExport() {
        return canExport;
    }

    public void setCanExport(Boolean canExport) {
        this.canExport = canExport;
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

    @Override
    public String toString() {
        return "RoleMenuGrant{" +
            "id = " + id +
            ", roleId = " + roleId +
            ", menuId = " + menuId +
            ", canView = " + canView +
            ", canCreate = " + canCreate +
            ", canUpdate = " + canUpdate +
            ", canDelete = " + canDelete +
            ", canExport = " + canExport +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
