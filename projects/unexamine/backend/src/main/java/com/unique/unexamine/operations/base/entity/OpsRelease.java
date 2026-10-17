package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 全局部署产物及兼容性版本目录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_release")
public class OpsRelease {

    /**
     * 产品发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 全局唯一的产品发布版本号
     */
    @TableField("version_name")
    private String versionName;

    /**
     * 部署产物校验摘要
     */
    @TableField("artifact_hash")
    private String artifactHash;

    /**
     * 数据库结构版本
     */
    @TableField("database_version")
    private String databaseVersion;

    /**
     * 配置结构版本
     */
    @TableField("config_version")
    private String configVersion;

    /**
     * 升级兼容性及环境要求
     */
    @TableField("compatibility_json")
    private String compatibilityJson;

    /**
     * 发布内容和升级说明
     */
    @TableField("release_notes")
    private String releaseNotes;

    /**
     * 发布版本状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 登记版本的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 发布版本登记时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getArtifactHash() {
        return artifactHash;
    }

    public void setArtifactHash(String artifactHash) {
        this.artifactHash = artifactHash;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public void setDatabaseVersion(String databaseVersion) {
        this.databaseVersion = databaseVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getCompatibilityJson() {
        return compatibilityJson;
    }

    public void setCompatibilityJson(String compatibilityJson) {
        this.compatibilityJson = compatibilityJson;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    @Override
    public String toString() {
        return "OpsRelease{" +
            "id = " + id +
            ", versionName = " + versionName +
            ", artifactHash = " + artifactHash +
            ", databaseVersion = " + databaseVersion +
            ", configVersion = " + configVersion +
            ", compatibilityJson = " + compatibilityJson +
            ", releaseNotes = " + releaseNotes +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
