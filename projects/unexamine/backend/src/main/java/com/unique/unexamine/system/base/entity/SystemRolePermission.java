package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户角色的正向资源动作授权
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_role_permission")
public class SystemRolePermission {

    /**
     * 系统角色授权主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 系统角色标识
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 授权资源类型
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 授权资源编码
     */
    @TableField("resource_code")
    private String resourceCode;

    /**
     * 允许执行的动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 数据范围类型
     */
    @TableField("data_scope_type")
    private String dataScopeType;

    /**
     * 数据范围参数
     */
    @TableField("data_scope_json")
    private String dataScopeJson;

    /**
     * 授权效果，仅允许正向授权
     */
    @TableField("effect")
    private String effect;

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

    /**
     * 乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getDataScopeType() {
        return dataScopeType;
    }

    public void setDataScopeType(String dataScopeType) {
        this.dataScopeType = dataScopeType;
    }

    public String getDataScopeJson() {
        return dataScopeJson;
    }

    public void setDataScopeJson(String dataScopeJson) {
        this.dataScopeJson = dataScopeJson;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SystemRolePermission{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", roleId = " + roleId +
            ", resourceType = " + resourceType +
            ", resourceCode = " + resourceCode +
            ", actionCode = " + actionCode +
            ", dataScopeType = " + dataScopeType +
            ", dataScopeJson = " + dataScopeJson +
            ", effect = " + effect +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
