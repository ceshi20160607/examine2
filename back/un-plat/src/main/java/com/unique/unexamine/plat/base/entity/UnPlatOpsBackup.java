package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 运维备份元数据
 * </p>
 *
 * @author Template Base
 * @since 2026-11-10
 */
@TableName("un_plat_ops_backup")
public class UnPlatOpsBackup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 运维备份主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台整库或单个业务系统作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统备份所属业务系统主键，平台整库时为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台整库SQL或系统数据SQL备份类型
     */
    @TableField("backup_kind")
    private String backupKind;

    /**
     * 创建中可用或失败状态
     */
    @TableField("status")
    private String status;

    /**
     * 服务端随机备份文件键
     */
    @TableField("artifact_key")
    private String artifactKey;

    /**
     * SQL备份文件字节数
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * SQL备份文件SHA256摘要
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 失败原因摘要，不记录数据库连接串
     */
    @TableField("error_text")
    private String errorText;

    /**
     * 发起备份的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

    /**
     * 备份发起时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 备份状态更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getBackupKind() {
        return backupKind;
    }

    public void setBackupKind(String backupKind) {
        this.backupKind = backupKind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getArtifactKey() {
        return artifactKey;
    }

    public void setArtifactKey(String artifactKey) {
        this.artifactKey = artifactKey;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
