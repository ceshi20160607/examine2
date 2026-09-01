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
@TableName("exchange_import_batch")
public class ExchangeImportBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("mapping_id")
    private Long mappingId;

    @TableField("source_file_id")
    private Long sourceFileId;

    @TableField("job_id")
    private Long jobId;

    @TableField("`mode`")
    private String mode;

    @TableField("`status`")
    private String status;

    @TableField("total_rows")
    private Long totalRows;

    @TableField("valid_rows")
    private Long validRows;

    @TableField("success_rows")
    private Long successRows;

    @TableField("failed_rows")
    private Long failedRows;

    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    @TableField("summary_json")
    private String summaryJson;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getValidRows() {
        return validRows;
    }

    public void setValidRows(Long validRows) {
        this.validRows = validRows;
    }

    public Long getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(Long successRows) {
        this.successRows = successRows;
    }

    public Long getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Long failedRows) {
        this.failedRows = failedRows;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ExchangeImportBatch{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", mappingId = " + mappingId +
            ", sourceFileId = " + sourceFileId +
            ", jobId = " + jobId +
            ", mode = " + mode +
            ", status = " + status +
            ", totalRows = " + totalRows +
            ", validRows = " + validRows +
            ", successRows = " + successRows +
            ", failedRows = " + failedRows +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", summaryJson = " + summaryJson +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", version = " + version +
            "}";
    }
}
