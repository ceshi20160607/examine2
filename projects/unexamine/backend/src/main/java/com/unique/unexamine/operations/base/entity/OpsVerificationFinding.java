package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 验收发现的问题及整改证据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_verification_finding")
public class OpsVerificationFinding {

    /**
     * 验收发现问题主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属验收运行主键
     */
    @TableField("verification_run_id")
    private Long verificationRunId;

    /**
     * 问题严重程度
     */
    @TableField("severity")
    private String severity;

    /**
     * 问题稳定编码
     */
    @TableField("finding_code")
    private String findingCode;

    /**
     * 问题标题
     */
    @TableField("title")
    private String title;

    /**
     * 复现步骤、预期与实际结果
     */
    @TableField("detail_text")
    private String detailText;

    /**
     * 日志、截图或读回数据证据引用
     */
    @TableField("evidence_json")
    private String evidenceJson;

    /**
     * 问题处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 解决问题的平台账号主键
     */
    @TableField("resolved_by_account_id")
    private Long resolvedByAccountId;

    /**
     * 问题解决时间
     */
    @TableField("resolve_time")
    private LocalDateTime resolveTime;

    /**
     * 问题登记时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 问题记录乐观锁版本
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

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
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
            ", resolveTime = " + resolveTime +
            ", createTime = " + createTime +
            ", version = " + version +
            "}";
    }
}
