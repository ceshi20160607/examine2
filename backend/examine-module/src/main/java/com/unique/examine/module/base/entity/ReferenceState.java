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
@TableName("un_module_reference_state")
public class ReferenceState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("record_id")
    private Long recordId;

    @TableField("schema_version_id")
    private Long schemaVersionId;

    @TableField("module_snapshot_id")
    private Long moduleSnapshotId;

    @TableField("field_snapshot_id")
    private Long fieldSnapshotId;

    @TableField("source_record_id")
    private Long sourceRecordId;

    @TableField("source_record_version")
    private Long sourceRecordVersion;

    @TableField("recalculation_state")
    private String recalculationState;

    @TableField("failure_correlation_id")
    private String failureCorrelationId;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
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

    public Long getFieldSnapshotId() {
        return fieldSnapshotId;
    }

    public void setFieldSnapshotId(Long fieldSnapshotId) {
        this.fieldSnapshotId = fieldSnapshotId;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getSourceRecordVersion() {
        return sourceRecordVersion;
    }

    public void setSourceRecordVersion(Long sourceRecordVersion) {
        this.sourceRecordVersion = sourceRecordVersion;
    }

    public String getRecalculationState() {
        return recalculationState;
    }

    public void setRecalculationState(String recalculationState) {
        this.recalculationState = recalculationState;
    }

    public String getFailureCorrelationId() {
        return failureCorrelationId;
    }

    public void setFailureCorrelationId(String failureCorrelationId) {
        this.failureCorrelationId = failureCorrelationId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
