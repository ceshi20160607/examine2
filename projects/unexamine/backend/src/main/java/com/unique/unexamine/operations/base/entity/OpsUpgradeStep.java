package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 升级步骤及失败处置证据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_upgrade_step")
public class OpsUpgradeStep {

    /**
     * 升级步骤主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属升级任务主键
     */
    @TableField("upgrade_id")
    private Long upgradeId;

    /**
     * 升级任务内执行顺序号
     */
    @TableField("step_number")
    private Integer stepNumber;

    /**
     * 数据库、配置、页面、流程或权限升级步骤类型
     */
    @TableField("step_type")
    private String stepType;

    /**
     * 步骤执行前版本
     */
    @TableField("source_version")
    private String sourceVersion;

    /**
     * 步骤目标版本
     */
    @TableField("target_version")
    private String targetVersion;

    /**
     * 配置或数据升级映射及校验依据
     */
    @TableField("mapping_json")
    private String mappingJson;

    /**
     * 步骤执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 升级步骤开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 升级步骤结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 步骤失败原因与处理提示
     */
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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", errorMessage = " + errorMessage +
            "}";
    }
}
