package com.unique.unexamine.authentication.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统租户权限的版本流水
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_auth_permission_version")
public class AuthorizationPermissionVersion {

    /**
     * 权限版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 权限范围类型，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统标识，平台版本为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识，平台版本为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 该范围内递增的权限版本号
     */
    @TableField("version_number")
    private Long versionNumber;

    /**
     * 权限版本变更原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 发起变更的平台账号标识
     */
    @TableField("changed_by_account_id")
    private Long changedByAccountId;

    /**
     * 版本生成时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getChangedByAccountId() {
        return changedByAccountId;
    }

    public void setChangedByAccountId(Long changedByAccountId) {
        this.changedByAccountId = changedByAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AuthorizationPermissionVersion{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", versionNumber = " + versionNumber +
            ", reason = " + reason +
            ", changedByAccountId = " + changedByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
