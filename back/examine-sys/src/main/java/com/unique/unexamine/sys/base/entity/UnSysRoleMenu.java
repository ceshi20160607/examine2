package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户角色菜单授权
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_sys_role_menu")
public class UnSysRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户角色菜单授权主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 租户菜单主键
     */
    @TableField("menu_id")
    private Long menuId;

    /**
     * 菜单内操作编码
     */
    @TableField("action_code")
    private String actionCode;

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

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
