package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台内业务系统
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_system")
public class SystemDefinition {

    /**
     * 业务系统标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 全平台唯一系统编码
     */
    @TableField("system_code")
    private String systemCode;

    /**
     * 业务系统名称
     */
    @TableField("system_name")
    private String systemName;

    /**
     * 当前系统所属平台账号标识
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 系统创建时同步生成的主租户标识
     */
    @TableField("primary_tenant_id")
    private Long primaryTenantId;

    /**
     * 多租户开关：0关闭，1开启
     */
    @TableField("multi_tenant_enabled")
    private Boolean multiTenantEnabled;

    /**
     * 系统状态：1启用，0停用
     */
    @TableField("`status`")
    private Boolean status;

    /**
     * 系统创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 系统修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getPrimaryTenantId() {
        return primaryTenantId;
    }

    public void setPrimaryTenantId(Long primaryTenantId) {
        this.primaryTenantId = primaryTenantId;
    }

    public Boolean getMultiTenantEnabled() {
        return multiTenantEnabled;
    }

    public void setMultiTenantEnabled(Boolean multiTenantEnabled) {
        this.multiTenantEnabled = multiTenantEnabled;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
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

    @Override
    public String toString() {
        return "SystemDefinition{" +
            "id = " + id +
            ", systemCode = " + systemCode +
            ", systemName = " + systemName +
            ", ownerAccountId = " + ownerAccountId +
            ", primaryTenantId = " + primaryTenantId +
            ", multiTenantEnabled = " + multiTenantEnabled +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
