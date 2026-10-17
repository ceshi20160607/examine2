package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统健康检查运行及结果汇总
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_health_run")
public class OpsHealthRun {

    /**
     * 健康检查运行主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 检查层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统级检查所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台或系统的稳定归属主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 健康检查类型
     */
    @TableField("run_type")
    private String runType;

    /**
     * 检查时运行的产品版本
     */
    @TableField("release_version")
    private String releaseVersion;

    /**
     * 健康检查结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 检查汇总和处置建议
     */
    @TableField("summary_json")
    private String summaryJson;

    /**
     * 发起检查的平台账号主键
     */
    @TableField("started_by_account_id")
    private Long startedByAccountId;

    /**
     * 健康检查开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 健康检查结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
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
        return "OpsHealthRun{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", runType = " + runType +
            ", releaseVersion = " + releaseVersion +
            ", status = " + status +
            ", summaryJson = " + summaryJson +
            ", startedByAccountId = " + startedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            "}";
    }
}
