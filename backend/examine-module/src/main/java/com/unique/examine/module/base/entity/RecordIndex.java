package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
@TableName("un_module_record_index")
public class RecordIndex implements Serializable {

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

    @TableField("normalization_generation_id")
    private Long normalizationGenerationId;

    @TableField("path_snapshot_id")
    private Long pathSnapshotId;

    @TableField("ordinal")
    private Integer ordinal;

    @TableField("record_status")
    private String recordStatus;

    @TableField("value_kind")
    private String valueKind;

    @TableField("string_value")
    private String stringValue;

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

    @TableField("reference_value")
    private Long referenceValue;

    @TableField("hash_value")
    private String hashValue;

    @TableField("hash_key_version")
    private String hashKeyVersion;

    @TableField("geohash")
    private String geohash;

    @TableField("geo_lat")
    private Double geoLat;

    @TableField("geo_lng")
    private Double geoLng;

    @TableField("currency_code")
    private String currencyCode;

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

    public Long getIndexGenerationId() {
        return indexGenerationId;
    }

    public void setIndexGenerationId(Long indexGenerationId) {
        this.indexGenerationId = indexGenerationId;
    }

    public Long getNormalizationGenerationId() {
        return normalizationGenerationId;
    }

    public void setNormalizationGenerationId(Long normalizationGenerationId) {
        this.normalizationGenerationId = normalizationGenerationId;
    }

    public Long getPathSnapshotId() {
        return pathSnapshotId;
    }

    public void setPathSnapshotId(Long pathSnapshotId) {
        this.pathSnapshotId = pathSnapshotId;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public String getValueKind() {
        return valueKind;
    }

    public void setValueKind(String valueKind) {
        this.valueKind = valueKind;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
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

    public Long getReferenceValue() {
        return referenceValue;
    }

    public void setReferenceValue(Long referenceValue) {
        this.referenceValue = referenceValue;
    }

    public String getHashValue() {
        return hashValue;
    }

    public void setHashValue(String hashValue) {
        this.hashValue = hashValue;
    }

    public String getHashKeyVersion() {
        return hashKeyVersion;
    }

    public void setHashKeyVersion(String hashKeyVersion) {
        this.hashKeyVersion = hashKeyVersion;
    }

    public String getGeohash() {
        return geohash;
    }

    public void setGeohash(String geohash) {
        this.geohash = geohash;
    }

    public Double getGeoLat() {
        return geoLat;
    }

    public void setGeoLat(Double geoLat) {
        this.geoLat = geoLat;
    }

    public Double getGeoLng() {
        return geoLng;
    }

    public void setGeoLng(Double geoLng) {
        this.geoLng = geoLng;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
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
