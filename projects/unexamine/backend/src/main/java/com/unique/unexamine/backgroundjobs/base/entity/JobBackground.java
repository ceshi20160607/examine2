package com.unique.unexamine.backgroundjobs.base.entity;

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
@TableName("job_background")
public class JobBackground {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_type")
    private String contextType;

    @TableField("platform_id")
    private Long platformId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("job_type")
    private String jobType;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private String sourceId;

    @TableField("parameter_json")
    private String parameterJson;

    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    @TableField("`status`")
    private String status;

    @TableField("progress_current")
    private Long progressCurrent;

    @TableField("progress_total")
    private Long progressTotal;

    @TableField("max_attempts")
    private Integer maxAttempts;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("next_run_at")
    private LocalDateTime nextRunAt;

    @TableField("heartbeat_at")
    private LocalDateTime heartbeatAt;

    @TableField("result_summary_json")
    private String resultSummaryJson;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by_account_id")
    private Long createdByAccountId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getParameterJson() {
        return parameterJson;
    }

    public void setParameterJson(String parameterJson) {
        this.parameterJson = parameterJson;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProgressCurrent() {
        return progressCurrent;
    }

    public void setProgressCurrent(Long progressCurrent) {
        this.progressCurrent = progressCurrent;
    }

    public Long getProgressTotal() {
        return progressTotal;
    }

    public void setProgressTotal(Long progressTotal) {
        this.progressTotal = progressTotal;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public LocalDateTime getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(LocalDateTime heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }

    public String getResultSummaryJson() {
        return resultSummaryJson;
    }

    public void setResultSummaryJson(String resultSummaryJson) {
        this.resultSummaryJson = resultSummaryJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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
        return "JobBackground{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", jobType = " + jobType +
            ", sourceType = " + sourceType +
            ", sourceId = " + sourceId +
            ", parameterJson = " + parameterJson +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", status = " + status +
            ", progressCurrent = " + progressCurrent +
            ", progressTotal = " + progressTotal +
            ", maxAttempts = " + maxAttempts +
            ", attemptCount = " + attemptCount +
            ", nextRunAt = " + nextRunAt +
            ", heartbeatAt = " + heartbeatAt +
            ", resultSummaryJson = " + resultSummaryJson +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", createdByAccountId = " + createdByAccountId +
            ", createdAt = " + createdAt +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
