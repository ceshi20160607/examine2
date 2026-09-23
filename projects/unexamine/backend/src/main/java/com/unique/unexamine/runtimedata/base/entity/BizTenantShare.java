package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 同系统跨租户记录及动态模块范围共享授权，不改变数据所有权
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_tenant_share")
public class BizTenantShare {

    /**
     * 共享授权主键，历史使用记录保持引用
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 共享双方所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 拥有业务数据的来源租户
     */
    @TableField("source_tenant_id")
    private Long sourceTenantId;

    /**
     * 获得访问关系的目标租户
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 共享数据所属模块
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 指定记录授权的原记录主键，动态范围授权为空
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 范围条件、字段类型绑定及授予时来源共享权限约束快照
     */
    @TableField("scope_json")
    private String scopeJson;

    /**
     * 明确允许的业务动作，仍与目标用户角色权限取交集
     */
    @TableField("permission_json")
    private String permissionJson;

    /**
     * 授权存储状态：ACTIVE有效，REVOKED已撤销
     */
    @TableField("`status`")
    private String status;

    /**
     * 授权开始生效时间，为空表示授予后立即生效
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 授权失效时间，旧接口和字段保持兼容
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 来源租户授予授权的系统成员
     */
    @TableField("granted_by_member_id")
    private Long grantedByMemberId;

    /**
     * 最近授予时间，旧字段保持兼容
     */
    @TableField("granted_at")
    private LocalDateTime grantedAt;

    /**
     * 授权撤销时间，历史授权不删除
     */
    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    /**
     * 授权修改的乐观锁版本
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

    public Long getSourceTenantId() {
        return sourceTenantId;
    }

    public void setSourceTenantId(Long sourceTenantId) {
        this.sourceTenantId = sourceTenantId;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeJson() {
        return scopeJson;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public String getPermissionJson() {
        return permissionJson;
    }

    public void setPermissionJson(String permissionJson) {
        this.permissionJson = permissionJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getGrantedByMemberId() {
        return grantedByMemberId;
    }

    public void setGrantedByMemberId(Long grantedByMemberId) {
        this.grantedByMemberId = grantedByMemberId;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BizTenantShare{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", sourceTenantId = " + sourceTenantId +
            ", targetTenantId = " + targetTenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", scopeType = " + scopeType +
            ", scopeJson = " + scopeJson +
            ", permissionJson = " + permissionJson +
            ", status = " + status +
            ", startTime = " + startTime +
            ", expiresAt = " + expiresAt +
            ", grantedByMemberId = " + grantedByMemberId +
            ", grantedAt = " + grantedAt +
            ", revokedAt = " + revokedAt +
            ", version = " + version +
            "}";
    }
}
