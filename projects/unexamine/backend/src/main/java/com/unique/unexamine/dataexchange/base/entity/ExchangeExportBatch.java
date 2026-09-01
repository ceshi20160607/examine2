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
@TableName("exchange_export_batch")
public class ExchangeExportBatch {

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

    @TableField("job_id")
    private Long jobId;

    @TableField("filter_snapshot_json")
    private String filterSnapshotJson;

    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    @TableField("selected_fields_json")
    private String selectedFieldsJson;

    @TableField("`status`")
    private String status;

    @TableField("total_rows")
    private Long totalRows;

    @TableField("exported_rows")
    private Long exportedRows;

    @TableField("result_file_id")
    private Long resultFileId;

    @TableField("error_message")
    private String errorMessage;

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

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getFilterSnapshotJson() {
        return filterSnapshotJson;
    }

    public void setFilterSnapshotJson(String filterSnapshotJson) {
        this.filterSnapshotJson = filterSnapshotJson;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getSelectedFieldsJson() {
        return selectedFieldsJson;
    }

    public void setSelectedFieldsJson(String selectedFieldsJson) {
        this.selectedFieldsJson = selectedFieldsJson;
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

    public Long getExportedRows() {
        return exportedRows;
    }

    public void setExportedRows(Long exportedRows) {
        this.exportedRows = exportedRows;
    }

    public Long getResultFileId() {
        return resultFileId;
    }

    public void setResultFileId(Long resultFileId) {
        this.resultFileId = resultFileId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
        return "ExchangeExportBatch{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", mappingId = " + mappingId +
            ", jobId = " + jobId +
            ", filterSnapshotJson = " + filterSnapshotJson +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", selectedFieldsJson = " + selectedFieldsJson +
            ", status = " + status +
            ", totalRows = " + totalRows +
            ", exportedRows = " + exportedRows +
            ", resultFileId = " + resultFileId +
            ", errorMessage = " + errorMessage +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", version = " + version +
            "}";
    }
}
