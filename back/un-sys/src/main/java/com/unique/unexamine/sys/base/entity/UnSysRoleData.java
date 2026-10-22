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
 * @since 2026-10-22
 */
@TableName("un_sys_role_data")
public class UnSysRoleData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统角色数据权限主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 对应操作菜单主键
     */
    @TableField("menu_id")
    private Long menuId;

    /**
     * 负责人和组织数据范围编码
     */
    @TableField("scope_code")
    private String scopeCode;

    /**
     * 指定负责人和部门范围
     */
    @TableField("scope_config")
    private String scopeConfig;

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

    public String getScopeCode() {
        return scopeCode;
    }

    public void setScopeCode(String scopeCode) {
        this.scopeCode = scopeCode;
    }

    public String getScopeConfig() {
        return scopeConfig;
    }

    public void setScopeConfig(String scopeConfig) {
        this.scopeConfig = scopeConfig;
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
