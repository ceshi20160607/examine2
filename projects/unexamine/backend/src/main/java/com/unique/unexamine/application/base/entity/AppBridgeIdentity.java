package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用跨系统调用身份映射
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_bridge_identity")
public class AppBridgeIdentity {

    /**
     * 应用身份映射主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 允许调用的来源系统主键
     */
    @TableField("source_system_id")
    private Long sourceSystemId;

    /**
     * 允许调用的来源租户主键
     */
    @TableField("source_tenant_id")
    private Long sourceTenantId;

    /**
     * 允许调用的来源租户成员主键
     */
    @TableField("source_tenant_member_id")
    private Long sourceTenantMemberId;

    /**
     * 目标执行系统主键
     */
    @TableField("target_system_id")
    private Long targetSystemId;

    /**
     * 目标执行租户主键
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 目标执行租户成员主键
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getSourceSystemId() {
        return sourceSystemId;
    }

    public void setSourceSystemId(Long sourceSystemId) {
        this.sourceSystemId = sourceSystemId;
    }

    public Long getSourceTenantId() {
        return sourceTenantId;
    }

    public void setSourceTenantId(Long sourceTenantId) {
        this.sourceTenantId = sourceTenantId;
    }

    public Long getSourceTenantMemberId() {
        return sourceTenantMemberId;
    }

    public void setSourceTenantMemberId(Long sourceTenantMemberId) {
        this.sourceTenantMemberId = sourceTenantMemberId;
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
        return "AppBridgeIdentity{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", sourceSystemId = " + sourceSystemId +
            ", sourceTenantId = " + sourceTenantId +
            ", sourceTenantMemberId = " + sourceTenantMemberId +
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
