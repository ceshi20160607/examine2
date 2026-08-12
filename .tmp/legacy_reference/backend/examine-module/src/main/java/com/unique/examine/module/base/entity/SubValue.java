package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-21
 */
@TableName("un_module_sub_value")
public class SubValue implements Serializable {

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

    @TableField("parent_field_snapshot_id")
    private Long parentFieldSnapshotId;

    @TableField("row_id")
    private Long rowId;

    @TableField("column_field_snapshot_id")
    private Long columnFieldSnapshotId;

    @TableField("source_field_id")
    private Long sourceFieldId;

    @TableField("logical_field_id")
    private Long logicalFieldId;

    @TableField("column_field_type")
    private String columnFieldType;

    @TableField("column_field_scope")
    private String columnFieldScope;

    @TableField("ordinal")
    private Integer ordinal;

    @TableField("string_value")
    private String stringValue;

    @TableField("text_value")
    private String textValue;

    @TableField("decimal_value")
    private BigDecimal decimalValue;

    @TableField("date_value")
    private LocalDate dateValue;

    @TableField("datetime_value")
    private LocalDateTime datetimeValue;

    @TableField("time_value")
    private LocalTime timeValue;

    @TableField("boolean_value")
    private Boolean booleanValue;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("reference_value")
    private Long referenceValue;

    @TableField("encrypted_value")
    private byte[] encryptedValue;

    @TableField("encryption_key_version")
    private String encryptionKeyVersion;

    @TableField("value_hash")
    private String valueHash;

    @TableField("hash_key_version")
    private String hashKeyVersion;

    @TableField("display_value")
    private String displayValue;

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

    public Long getParentFieldSnapshotId() {
        return parentFieldSnapshotId;
    }

    public void setParentFieldSnapshotId(Long parentFieldSnapshotId) {
        this.parentFieldSnapshotId = parentFieldSnapshotId;
    }

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
    }

    public Long getColumnFieldSnapshotId() {
        return columnFieldSnapshotId;
    }

    public void setColumnFieldSnapshotId(Long columnFieldSnapshotId) {
        this.columnFieldSnapshotId = columnFieldSnapshotId;
    }

    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    public Long getLogicalFieldId() {
        return logicalFieldId;
    }

    public void setLogicalFieldId(Long logicalFieldId) {
        this.logicalFieldId = logicalFieldId;
    }

    public String getColumnFieldType() {
        return columnFieldType;
    }

    public void setColumnFieldType(String columnFieldType) {
        this.columnFieldType = columnFieldType;
    }

    public String getColumnFieldScope() {
        return columnFieldScope;
    }

    public void setColumnFieldScope(String columnFieldScope) {
        this.columnFieldScope = columnFieldScope;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public BigDecimal getDecimalValue() {
        return decimalValue;
    }

    public void setDecimalValue(BigDecimal decimalValue) {
        this.decimalValue = decimalValue;
    }

    public LocalDate getDateValue() {
        return dateValue;
    }

    public void setDateValue(LocalDate dateValue) {
        this.dateValue = dateValue;
    }

    public LocalDateTime getDatetimeValue() {
        return datetimeValue;
    }

    public void setDatetimeValue(LocalDateTime datetimeValue) {
        this.datetimeValue = datetimeValue;
    }

    public LocalTime getTimeValue() {
        return timeValue;
    }

    public void setTimeValue(LocalTime timeValue) {
        this.timeValue = timeValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Long getReferenceValue() {
        return referenceValue;
    }

    public void setReferenceValue(Long referenceValue) {
        this.referenceValue = referenceValue;
    }

    public byte[] getEncryptedValue() {
        return encryptedValue;
    }

    public void setEncryptedValue(byte[] encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public String getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    public void setEncryptionKeyVersion(String encryptionKeyVersion) {
        this.encryptionKeyVersion = encryptionKeyVersion;
    }

    public String getValueHash() {
        return valueHash;
    }

    public void setValueHash(String valueHash) {
        this.valueHash = valueHash;
    }

    public String getHashKeyVersion() {
        return hashKeyVersion;
    }

    public void setHashKeyVersion(String hashKeyVersion) {
        this.hashKeyVersion = hashKeyVersion;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
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
