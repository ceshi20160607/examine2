package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-21
 */
@TableName("un_module_sub_record")
public class SubRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("parent_record_id")
    private Long parentRecordId;

    @TableField("schema_version_id")
    private Long schemaVersionId;

    @TableField("module_snapshot_id")
    private Long moduleSnapshotId;

    @TableField("logical_module_id")
    private Long logicalModuleId;

    @TableField("parent_field_snapshot_id")
    private Long parentFieldSnapshotId;

    @TableField("parent_logical_field_id")
    private Long parentLogicalFieldId;

    @TableField("parent_field_type")
    private String parentFieldType;

    @TableField("parent_field_scope")
    private String parentFieldScope;

    @TableField("row_id")
    private Long rowId;

    @TableField("client_row_key")
    private String clientRowKey;

    @TableField("ordinal")
    private Integer ordinal;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @Version
    @TableField("version")
    private Long version;

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

    public Long getParentRecordId() {
        return parentRecordId;
    }

    public void setParentRecordId(Long parentRecordId) {
        this.parentRecordId = parentRecordId;
    }

    public Long getSchemaVersionId() {
        return schemaVersionId;
    }

    public void setSchemaVersionId(Long schemaVersionId) {
        this.schemaVersionId = schemaVersionId;
    }

    public Long getModuleSnapshotId() {
        return moduleSnapshotId;
    }

    public void setModuleSnapshotId(Long moduleSnapshotId) {
        this.moduleSnapshotId = moduleSnapshotId;
    }

    public Long getLogicalModuleId() {
        return logicalModuleId;
    }

    public void setLogicalModuleId(Long logicalModuleId) {
        this.logicalModuleId = logicalModuleId;
    }

    public Long getParentFieldSnapshotId() {
        return parentFieldSnapshotId;
    }

    public void setParentFieldSnapshotId(Long parentFieldSnapshotId) {
        this.parentFieldSnapshotId = parentFieldSnapshotId;
    }

    public Long getParentLogicalFieldId() {
        return parentLogicalFieldId;
    }

    public void setParentLogicalFieldId(Long parentLogicalFieldId) {
        this.parentLogicalFieldId = parentLogicalFieldId;
    }

    public String getParentFieldType() {
        return parentFieldType;
    }

    public void setParentFieldType(String parentFieldType) {
        this.parentFieldType = parentFieldType;
    }

    public String getParentFieldScope() {
        return parentFieldScope;
    }

    public void setParentFieldScope(String parentFieldScope) {
        this.parentFieldScope = parentFieldScope;
    }

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
    }

    public String getClientRowKey() {
        return clientRowKey;
    }

    public void setClientRowKey(String clientRowKey) {
        this.clientRowKey = clientRowKey;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
