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
@TableName("un_module_record_relation")
public class RecordRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("source_record_id")
    private Long sourceRecordId;

    @TableField("source_schema_version_id")
    private Long sourceSchemaVersionId;

    @TableField("source_module_snapshot_id")
    private Long sourceModuleSnapshotId;

    @TableField("source_logical_module_id")
    private Long sourceLogicalModuleId;

    @TableField("source_field_snapshot_id")
    private Long sourceFieldSnapshotId;

    @TableField("source_logical_field_id")
    private Long sourceLogicalFieldId;

    @TableField("source_field_type")
    private String sourceFieldType;

    @TableField("source_field_scope")
    private String sourceFieldScope;

    @TableField("target_record_id")
    private Long targetRecordId;

    @TableField("target_schema_version_id")
    private Long targetSchemaVersionId;

    @TableField("target_module_snapshot_id")
    private Long targetModuleSnapshotId;

    @TableField("target_logical_module_id")
    private Long targetLogicalModuleId;

    @TableField("ordinal")
    private Integer ordinal;

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

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getSourceSchemaVersionId() {
        return sourceSchemaVersionId;
    }

    public void setSourceSchemaVersionId(Long sourceSchemaVersionId) {
        this.sourceSchemaVersionId = sourceSchemaVersionId;
    }

    public Long getSourceModuleSnapshotId() {
        return sourceModuleSnapshotId;
    }

    public void setSourceModuleSnapshotId(Long sourceModuleSnapshotId) {
        this.sourceModuleSnapshotId = sourceModuleSnapshotId;
    }

    public Long getSourceLogicalModuleId() {
        return sourceLogicalModuleId;
    }

    public void setSourceLogicalModuleId(Long sourceLogicalModuleId) {
        this.sourceLogicalModuleId = sourceLogicalModuleId;
    }

    public Long getSourceFieldSnapshotId() {
        return sourceFieldSnapshotId;
    }

    public void setSourceFieldSnapshotId(Long sourceFieldSnapshotId) {
        this.sourceFieldSnapshotId = sourceFieldSnapshotId;
    }

    public Long getSourceLogicalFieldId() {
        return sourceLogicalFieldId;
    }

    public void setSourceLogicalFieldId(Long sourceLogicalFieldId) {
        this.sourceLogicalFieldId = sourceLogicalFieldId;
    }

    public String getSourceFieldType() {
        return sourceFieldType;
    }

    public void setSourceFieldType(String sourceFieldType) {
        this.sourceFieldType = sourceFieldType;
    }

    public String getSourceFieldScope() {
        return sourceFieldScope;
    }

    public void setSourceFieldScope(String sourceFieldScope) {
        this.sourceFieldScope = sourceFieldScope;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public Long getTargetSchemaVersionId() {
        return targetSchemaVersionId;
    }

    public void setTargetSchemaVersionId(Long targetSchemaVersionId) {
        this.targetSchemaVersionId = targetSchemaVersionId;
    }

    public Long getTargetModuleSnapshotId() {
        return targetModuleSnapshotId;
    }

    public void setTargetModuleSnapshotId(Long targetModuleSnapshotId) {
        this.targetModuleSnapshotId = targetModuleSnapshotId;
    }

    public Long getTargetLogicalModuleId() {
        return targetLogicalModuleId;
    }

    public void setTargetLogicalModuleId(Long targetLogicalModuleId) {
        this.targetLogicalModuleId = targetLogicalModuleId;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
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
