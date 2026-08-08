package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-20
 */
@TableName("un_module_record_unique")
public class RecordUnique implements Serializable {

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

    @TableField("logical_module_id")
    private Long logicalModuleId;

    @TableField("logical_field_id")
    private Long logicalFieldId;

    @TableField("normalization_generation_id")
    private Long normalizationGenerationId;

    @TableField("field_type")
    private String fieldType;

    @TableField("record_status")
    private String recordStatus;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("normalized_hash")
    private String normalizedHash;

    @TableField("hash_key_version")
    private String hashKeyVersion;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public Long getLogicalModuleId() {
        return logicalModuleId;
    }

    public void setLogicalModuleId(Long logicalModuleId) {
        this.logicalModuleId = logicalModuleId;
    }

    public Long getLogicalFieldId() {
        return logicalFieldId;
    }

    public void setLogicalFieldId(Long logicalFieldId) {
        this.logicalFieldId = logicalFieldId;
    }

    public Long getNormalizationGenerationId() {
        return normalizationGenerationId;
    }

    public void setNormalizationGenerationId(Long normalizationGenerationId) {
        this.normalizationGenerationId = normalizationGenerationId;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getNormalizedHash() {
        return normalizedHash;
    }

    public void setNormalizedHash(String normalizedHash) {
        this.normalizedHash = normalizedHash;
    }

    public String getHashKeyVersion() {
        return hashKeyVersion;
    }

    public void setHashKeyVersion(String hashKeyVersion) {
        this.hashKeyVersion = hashKeyVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
