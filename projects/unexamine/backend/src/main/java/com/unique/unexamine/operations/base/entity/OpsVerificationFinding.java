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
@TableName("ops_verification_finding")
public class OpsVerificationFinding {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("verification_run_id")
    private Long verificationRunId;

    @TableField("severity")
    private String severity;

    @TableField("finding_code")
    private String findingCode;

    @TableField("title")
    private String title;

    @TableField("detail_text")
    private String detailText;

    @TableField("evidence_json")
    private String evidenceJson;

    @TableField("`status`")
    private String status;

    @TableField("resolved_by_account_id")
    private Long resolvedByAccountId;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

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

    public Long getVerificationRunId() {
        return verificationRunId;
    }

    public void setVerificationRunId(Long verificationRunId) {
        this.verificationRunId = verificationRunId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getFindingCode() {
        return findingCode;
    }

    public void setFindingCode(String findingCode) {
        this.findingCode = findingCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetailText() {
        return detailText;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getResolvedByAccountId() {
        return resolvedByAccountId;
    }

    public void setResolvedByAccountId(Long resolvedByAccountId) {
        this.resolvedByAccountId = resolvedByAccountId;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
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
        return "OpsVerificationFinding{" +
            "id = " + id +
            ", verificationRunId = " + verificationRunId +
            ", severity = " + severity +
            ", findingCode = " + findingCode +
            ", title = " + title +
            ", detailText = " + detailText +
            ", evidenceJson = " + evidenceJson +
            ", status = " + status +
            ", resolvedByAccountId = " + resolvedByAccountId +
            ", resolvedAt = " + resolvedAt +
            ", createdAt = " + createdAt +
            ", version = " + version +
            "}";
    }
}
