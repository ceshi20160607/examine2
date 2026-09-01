package com.unique.unexamine.print.base.entity;

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
@TableName("print_job")
public class PrintJob {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("record_id")
    private Long recordId;

    @TableField("template_version_id")
    private Long templateVersionId;

    @TableField("background_job_id")
    private Long backgroundJobId;

    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    @TableField("record_snapshot_json")
    private String recordSnapshotJson;

    @TableField("`status`")
    private String status;

    @TableField("output_file_id")
    private Long outputFileId;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public Long getBackgroundJobId() {
        return backgroundJobId;
    }

    public void setBackgroundJobId(Long backgroundJobId) {
        this.backgroundJobId = backgroundJobId;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getRecordSnapshotJson() {
        return recordSnapshotJson;
    }

    public void setRecordSnapshotJson(String recordSnapshotJson) {
        this.recordSnapshotJson = recordSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOutputFileId() {
        return outputFileId;
    }

    public void setOutputFileId(Long outputFileId) {
        this.outputFileId = outputFileId;
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
        return "PrintJob{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", templateVersionId = " + templateVersionId +
            ", backgroundJobId = " + backgroundJobId +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", recordSnapshotJson = " + recordSnapshotJson +
            ", status = " + status +
            ", outputFileId = " + outputFileId +
            ", errorMessage = " + errorMessage +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", finishedAt = " + finishedAt +
            ", version = " + version +
            "}";
    }
}
