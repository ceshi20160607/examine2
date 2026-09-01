package com.unique.unexamine.operations.base.entity;

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
@TableName("ops_deployment")
public class OpsDeployment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("release_id")
    private Long releaseId;

    @TableField("environment_code")
    private String environmentCode;

    @TableField("deployment_type")
    private String deploymentType;

    @TableField("from_release_id")
    private Long fromReleaseId;

    @TableField("`status`")
    private String status;

    @TableField("step_state_json")
    private String stepStateJson;

    @TableField("rollback_point_json")
    private String rollbackPointJson;

    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public Long getFromReleaseId() {
        return fromReleaseId;
    }

    public void setFromReleaseId(Long fromReleaseId) {
        this.fromReleaseId = fromReleaseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStepStateJson() {
        return stepStateJson;
    }

    public void setStepStateJson(String stepStateJson) {
        this.stepStateJson = stepStateJson;
    }

    public String getRollbackPointJson() {
        return rollbackPointJson;
    }

    public void setRollbackPointJson(String rollbackPointJson) {
        this.rollbackPointJson = rollbackPointJson;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsDeployment{" +
            "id = " + id +
            ", releaseId = " + releaseId +
            ", environmentCode = " + environmentCode +
            ", deploymentType = " + deploymentType +
            ", fromReleaseId = " + fromReleaseId +
            ", status = " + status +
            ", stepStateJson = " + stepStateJson +
            ", rollbackPointJson = " + rollbackPointJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", createdAt = " + createdAt +
            ", version = " + version +
            "}";
    }
}
