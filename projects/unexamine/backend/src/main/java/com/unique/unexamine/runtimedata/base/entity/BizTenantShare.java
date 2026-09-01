package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_tenant_share")
public class BizTenantShare {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("source_tenant_id")
    private Long sourceTenantId;

    @TableField("target_tenant_id")
    private Long targetTenantId;

    @TableField("record_id")
    private Long recordId;

    @TableField("permission_json")
    private String permissionJson;

    @TableField("`status`")
    private String status;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("granted_by_member_id")
    private Long grantedByMemberId;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
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
            ", recordId = " + recordId +
            ", permissionJson = " + permissionJson +
            ", status = " + status +
            ", expiresAt = " + expiresAt +
            ", grantedByMemberId = " + grantedByMemberId +
            ", grantedAt = " + grantedAt +
            ", revokedAt = " + revokedAt +
            ", version = " + version +
            "}";
    }
}
