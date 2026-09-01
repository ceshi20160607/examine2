package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ops_secret_rotation")
public class OpsSecretRotation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("secret_ref_id")
    private Long secretRefId;

    @TableField("from_version")
    private String fromVersion;

    @TableField("to_version")
    private String toVersion;

    @TableField("`status`")
    private String status;

    @TableField("verification_json")
    private String verificationJson;

    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSecretRefId() {
        return secretRefId;
    }

    public void setSecretRefId(Long secretRefId) {
        this.secretRefId = secretRefId;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationJson() {
        return verificationJson;
    }

    public void setVerificationJson(String verificationJson) {
        this.verificationJson = verificationJson;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsSecretRotation{" +
            "id = " + id +
            ", secretRefId = " + secretRefId +
            ", fromVersion = " + fromVersion +
            ", toVersion = " + toVersion +
            ", status = " + status +
            ", verificationJson = " + verificationJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", createdAt = " + createdAt +
            ", version = " + version +
            "}";
    }
}
