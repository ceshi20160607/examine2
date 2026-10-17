package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 分范围备份任务及加密密钥引用
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_backup")
public class OpsBackup {

    /**
     * 备份任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 备份层级，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统备份所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台或系统的稳定归属主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 数据库、文件、配置或密钥策略等备份类型
     */
    @TableField("backup_type")
    private String backupType;

    /**
     * 备份执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 备份清单校验摘要
     */
    @TableField("manifest_hash")
    private String manifestHash;

    /**
     * 同范围备份加密密钥引用主键，不保存密钥明文
     */
    @TableField("encryption_secret_ref_id")
    private Long encryptionSecretRefId;

    /**
     * 备份保留截止时间
     */
    @TableField("retention_until_time")
    private LocalDateTime retentionUntilTime;

    /**
     * 发起备份的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 备份开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 备份结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 备份申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 备份记录乐观锁版本
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

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public void setManifestHash(String manifestHash) {
        this.manifestHash = manifestHash;
    }

    public Long getEncryptionSecretRefId() {
        return encryptionSecretRefId;
    }

    public void setEncryptionSecretRefId(Long encryptionSecretRefId) {
        this.encryptionSecretRefId = encryptionSecretRefId;
    }

    public LocalDateTime getRetentionUntilTime() {
        return retentionUntilTime;
    }

    public void setRetentionUntilTime(LocalDateTime retentionUntilTime) {
        this.retentionUntilTime = retentionUntilTime;
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
        return "OpsBackup{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", backupType = " + backupType +
            ", status = " + status +
            ", manifestHash = " + manifestHash +
            ", encryptionSecretRefId = " + encryptionSecretRefId +
            ", retentionUntilTime = " + retentionUntilTime +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", createTime = " + createTime +
            ", version = " + version +
            "}";
    }
}
