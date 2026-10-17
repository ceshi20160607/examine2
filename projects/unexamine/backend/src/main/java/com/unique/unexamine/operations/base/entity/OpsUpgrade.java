package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 有备份和回滚点的产品升级任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_upgrade")
public class OpsUpgrade {

    /**
     * 产品升级任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 升级目标发布版本主键
     */
    @TableField("release_id")
    private Long releaseId;

    /**
     * 升级前同范围备份任务主键
     */
    @TableField("backup_id")
    private Long backupId;

    /**
     * 升级层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统级升级所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台或系统的稳定归属主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 升级前模块、权限、数据和兼容性影响报告
     */
    @TableField("impact_report_json")
    private String impactReportJson;

    /**
     * 升级执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 失败时可恢复的回滚点及处置方案
     */
    @TableField("rollback_point_json")
    private String rollbackPointJson;

    /**
     * 发起升级的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 升级开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 升级结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 升级申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 升级任务乐观锁版本
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

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public Long getBackupId() {
        return backupId;
    }

    public void setBackupId(Long backupId) {
        this.backupId = backupId;
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

    public String getImpactReportJson() {
        return impactReportJson;
    }

    public void setImpactReportJson(String impactReportJson) {
        this.impactReportJson = impactReportJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsUpgrade{" +
            "id = " + id +
            ", releaseId = " + releaseId +
            ", backupId = " + backupId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", impactReportJson = " + impactReportJson +
            ", status = " + status +
            ", rollbackPointJson = " + rollbackPointJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", createTime = " + createTime +
            ", version = " + version +
            "}";
    }
}
