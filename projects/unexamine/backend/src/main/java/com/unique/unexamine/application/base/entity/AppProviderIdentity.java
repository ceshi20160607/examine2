package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台成员映射
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_provider_identity")
public class AppProviderIdentity {

    /**
     * 外部平台成员映射主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属外部平台连接主键
     */
    @TableField("connection_id")
    private Long connectionId;

    /**
     * 平台侧稳定用户标识，例如企业微信UserID
     */
    @TableField("external_user_id")
    private String externalUserId;

    /**
     * 目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标平台主键
     */
    @TableField("target_platform_id")
    private Long targetPlatformId;

    /**
     * 目标平台成员主键
     */
    @TableField("target_platform_member_id")
    private Long targetPlatformMemberId;

    /**
     * 目标系统主键
     */
    @TableField("target_system_id")
    private Long targetSystemId;

    /**
     * 目标租户主键
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 目标租户成员主键
     */
    @TableField("target_tenant_member_id")
    private Long targetTenantMemberId;

    /**
     * 映射状态：ACTIVE启用、DISABLED停用
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建人平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

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

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetPlatformId() {
        return targetPlatformId;
    }

    public void setTargetPlatformId(Long targetPlatformId) {
        this.targetPlatformId = targetPlatformId;
    }

    public Long getTargetPlatformMemberId() {
        return targetPlatformMemberId;
    }

    public void setTargetPlatformMemberId(Long targetPlatformMemberId) {
        this.targetPlatformMemberId = targetPlatformMemberId;
    }

    public Long getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(Long targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
    }

    public Long getTargetTenantMemberId() {
        return targetTenantMemberId;
    }

    public void setTargetTenantMemberId(Long targetTenantMemberId) {
        this.targetTenantMemberId = targetTenantMemberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppProviderIdentity{" +
            "id = " + id +
            ", connectionId = " + connectionId +
            ", externalUserId = " + externalUserId +
            ", targetType = " + targetType +
            ", targetPlatformId = " + targetPlatformId +
            ", targetPlatformMemberId = " + targetPlatformMemberId +
            ", targetSystemId = " + targetSystemId +
            ", targetTenantId = " + targetTenantId +
            ", targetTenantMemberId = " + targetTenantMemberId +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", version = " + version +
            "}";
    }
}
