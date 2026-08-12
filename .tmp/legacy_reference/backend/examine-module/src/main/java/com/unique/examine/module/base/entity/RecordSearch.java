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
@TableName("un_module_record_search")
public class RecordSearch implements Serializable {

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

    @TableField("index_generation_id")
    private Long indexGenerationId;

    @TableField("record_status")
    private String recordStatus;

    @TableField("field_type")
    private String fieldType;

    @TableField("token_ordinal")
    private Integer tokenOrdinal;

    @TableField("token")
    private String token;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public Long getIndexGenerationId() {
        return indexGenerationId;
    }

    public void setIndexGenerationId(Long indexGenerationId) {
        this.indexGenerationId = indexGenerationId;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Integer getTokenOrdinal() {
        return tokenOrdinal;
    }

    public void setTokenOrdinal(Integer tokenOrdinal) {
        this.tokenOrdinal = tokenOrdinal;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
