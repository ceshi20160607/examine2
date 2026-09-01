package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ops_verification_run")
public class OpsVerificationRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("verification_type")
    private String verificationType;

    @TableField("context_type")
    private String contextType;

    @TableField("system_id")
    private Long systemId;

    @TableField("release_id")
    private Long releaseId;

    @TableField("scenario_code")
    private String scenarioCode;

    @TableField("`status`")
    private String status;

    @TableField("input_json")
    private String inputJson;

    @TableField("result_json")
    private String resultJson;

    @TableField("threshold_json")
    private String thresholdJson;

    @TableField("started_by_account_id")
    private Long startedByAccountId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public void setScenarioCode(String scenarioCode) {
        this.scenarioCode = scenarioCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getThresholdJson() {
        return thresholdJson;
    }

    public void setThresholdJson(String thresholdJson) {
        this.thresholdJson = thresholdJson;
    }

    public Long getStartedByAccountId() {
        return startedByAccountId;
    }

    public void setStartedByAccountId(Long startedByAccountId) {
        this.startedByAccountId = startedByAccountId;
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

    @Override
    public String toString() {
        return "OpsVerificationRun{" +
            "id = " + id +
            ", verificationType = " + verificationType +
            ", contextType = " + contextType +
            ", systemId = " + systemId +
            ", releaseId = " + releaseId +
            ", scenarioCode = " + scenarioCode +
            ", status = " + status +
            ", inputJson = " + inputJson +
            ", resultJson = " + resultJson +
            ", thresholdJson = " + thresholdJson +
            ", startedByAccountId = " + startedByAccountId +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            "}";
    }
}
