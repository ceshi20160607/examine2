package com.unique.unexamine.backgroundjobs.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 后台作业业务申请与最终审计；运行状态和重试事实保存在 Redis
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_job_background")
public class JobBackground {

    /**
     * 后台作业业务申请主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 申请层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统层申请所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统层申请所属租户主键，可为空表示系统公共作业
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作业业务类型，如导入、导出、打印或文件处理
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 发起作业的业务对象类型
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 发起作业的业务对象标识
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 提交时固定的业务参数，不包含运行队列状态
     */
    @TableField("parameter_json")
    private String parameterJson;

    /**
     * 申请时固定的权限与数据范围快照
     */
    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    /**
     * 完成后写入的最终审计状态，成功、带警告成功、失败或取消
     */
    @TableField("final_status")
    private String finalStatus;

    /**
     * 最终结果、结果文件引用及统计摘要
     */
    @TableField("result_summary_json")
    private String resultSummaryJson;

    /**
     * 最终失败错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 最终失败原因摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 发起申请的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 业务申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最终结果归档时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 业务审计记录更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 业务审计记录乐观锁版本
     */
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

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", finalStatus = " + finalStatus +
            ", resultSummaryJson = " + resultSummaryJson +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
