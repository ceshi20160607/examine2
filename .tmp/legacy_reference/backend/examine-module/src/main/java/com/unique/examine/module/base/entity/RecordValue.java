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
 * @since 2026-07-20
 */
@TableName("un_module_record_value")
public class RecordValue implements Serializable {

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

    @TableField("field_snapshot_id")
    private Long fieldSnapshotId;

    @TableField("logical_field_id")
    private Long logicalFieldId;

    @TableField("field_version")
    private Long fieldVersion;

    @TableField("field_type")
    private String fieldType;

    @TableField("field_scope")
    private String fieldScope;

    @TableField("result_schema")
    private String resultSchema;

    @TableField("dependency_version_json")
    private String dependencyVersionJson;

    @TableField("evaluator_version")
    private Short evaluatorVersion;

    @TableField("recalculation_state")
    private String recalculationState;

    @TableField("failure_correlation_id")
    private String failureCorrelationId;

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

    public Long getFieldSnapshotId() {
        return fieldSnapshotId;
    }

    public void setFieldSnapshotId(Long fieldSnapshotId) {
        this.fieldSnapshotId = fieldSnapshotId;
    }

    public Long getLogicalFieldId() {
        return logicalFieldId;
    }

    public void setLogicalFieldId(Long logicalFieldId) {
        this.logicalFieldId = logicalFieldId;
    }

    public Long getFieldVersion() {
        return fieldVersion;
    }

    public void setFieldVersion(Long fieldVersion) {
        this.fieldVersion = fieldVersion;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldScope() {
        return fieldScope;
    }

    public void setFieldScope(String fieldScope) {
        this.fieldScope = fieldScope;
    }

    public String getResultSchema() {
        return resultSchema;
    }

    public void setResultSchema(String resultSchema) {
        this.resultSchema = resultSchema;
    }

    public String getDependencyVersionJson() {
        return dependencyVersionJson;
    }

    public void setDependencyVersionJson(String dependencyVersionJson) {
        this.dependencyVersionJson = dependencyVersionJson;
    }

    public Short getEvaluatorVersion() {
        return evaluatorVersion;
    }

    public void setEvaluatorVersion(Short evaluatorVersion) {
        this.evaluatorVersion = evaluatorVersion;
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
