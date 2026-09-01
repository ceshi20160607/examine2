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
@TableName("ops_release")
public class OpsRelease {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("version_name")
    private String versionName;

    @TableField("artifact_hash")
    private String artifactHash;

    @TableField("database_version")
    private String databaseVersion;

    @TableField("config_version")
    private String configVersion;

    @TableField("compatibility_json")
    private String compatibilityJson;

    @TableField("release_notes")
    private String releaseNotes;

    @TableField("`status`")
    private String status;

    @TableField("created_by_account_id")
    private Long createdByAccountId;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
            ", createdAt = " + createdAt +
            "}";
    }
}
