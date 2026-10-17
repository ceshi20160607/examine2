package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 可复现的产品功能与部署验收记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_verification_run")
public class OpsVerificationRun {

    /**
     * 产品验收运行主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 验收类型，如部署、升级或业务场景
     */
    @TableField("verification_type")
    private String verificationType;

    /**
     * 验收层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统验收所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台或系统的稳定归属主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 验收对应的产品发布版本主键
     */
    @TableField("release_id")
    private Long releaseId;

    /**
     * 可复现的验收场景编码
     */
    @TableField("scenario_code")
    private String scenarioCode;

    /**
     * 验收结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 验收输入、前置条件和步骤参数
     */
    @TableField("input_json")
    private String inputJson;

    /**
     * 验收读回结果及业务证据
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 验收通过条件和限制
     */
    @TableField("threshold_json")
    private String thresholdJson;

    /**
     * 发起验收的平台账号主键
     */
    @TableField("started_by_account_id")
    private Long startedByAccountId;

    /**
     * 验收开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 验收结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    @Override
    public String toString() {
        return "OpsVerificationRun{" +
            "id = " + id +
            ", verificationType = " + verificationType +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", releaseId = " + releaseId +
            ", scenarioCode = " + scenarioCode +
            ", status = " + status +
            ", inputJson = " + inputJson +
            ", resultJson = " + resultJson +
            ", thresholdJson = " + thresholdJson +
            ", startedByAccountId = " + startedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            "}";
    }
}
