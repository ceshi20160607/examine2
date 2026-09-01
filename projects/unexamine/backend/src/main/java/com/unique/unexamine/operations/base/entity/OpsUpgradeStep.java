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
@TableName("ops_upgrade_step")
public class OpsUpgradeStep {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("upgrade_id")
    private Long upgradeId;

    @TableField("step_number")
    private Integer stepNumber;

    @TableField("step_type")
    private String stepType;

    @TableField("source_version")
    private String sourceVersion;

    @TableField("target_version")
    private String targetVersion;

    @TableField("mapping_json")
    private String mappingJson;

    @TableField("`status`")
    private String status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("error_message")
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUpgradeId() {
        return upgradeId;
    }

    public void setUpgradeId(Long upgradeId) {
        this.upgradeId = upgradeId;
    }

    public Integer getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(Integer stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getMappingJson() {
        return mappingJson;
    }

    public void setMappingJson(String mappingJson) {
        this.mappingJson = mappingJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "OpsUpgradeStep{" +
            "id = " + id +
            ", upgradeId = " + upgradeId +
            ", stepNumber = " + stepNumber +
            ", stepType = " + stepType +
            ", sourceVersion = " + sourceVersion +
            ", targetVersion = " + targetVersion +
            ", mappingJson = " + mappingJson +
            ", status = " + status +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", errorMessage = " + errorMessage +
            "}";
    }
}
