package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台角色数据权限
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_plat_role_data")
public class UnPlatRoleData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台角色数据权限主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 数据资源编码
     */
    @TableField("resource_code")
    private String resourceCode;

    /**
     * 数据范围编码
     */
    @TableField("scope_code")
    private String scopeCode;

    /**
     * 数据范围补充配置
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

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
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
