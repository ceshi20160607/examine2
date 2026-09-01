package com.unique.unexamine.dataexchange.base.entity;

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
@TableName("exchange_import_row")
public class ExchangeImportRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("`row_number`")
    private Long rowNumber;

    @TableField("raw_json")
    private String rawJson;

    @TableField("normalized_json")
    private String normalizedJson;

    @TableField("`status`")
    private String status;

    @TableField("target_record_id")
    private Long targetRecordId;

    @TableField("error_json")
    private String errorJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getNormalizedJson() {
        return normalizedJson;
    }

    public void setNormalizedJson(String normalizedJson) {
        this.normalizedJson = normalizedJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ExchangeImportRow{" +
            "id = " + id +
            ", batchId = " + batchId +
            ", rowNumber = " + rowNumber +
            ", rawJson = " + rawJson +
            ", normalizedJson = " + normalizedJson +
            ", status = " + status +
            ", targetRecordId = " + targetRecordId +
            ", errorJson = " + errorJson +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
