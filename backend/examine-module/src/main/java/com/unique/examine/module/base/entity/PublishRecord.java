package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@TableName("un_module_publish_record")
public class PublishRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("operation_type")
    private String operationType;

    @TableField("from_version_id")
    private Long fromVersionId;

    @TableField("to_version_id")
    private Long toVersionId;

    @TableField("target_version_id")
    private Long targetVersionId;

    @TableField("check_id")
    private Long checkId;

    @TableField("draft_revision")
    private Long draftRevision;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("request_id")
    private String requestId;

    @TableField("trace_id")
    private String traceId;

    @TableField("result")
    private String result;

    @TableField("impact_report_json")
    private String impactReportJson;

    @TableField("operated_at")
    private LocalDateTime operatedAt;

    @TableField("operated_by")
    private Long operatedBy;

    @TableField("reason")
    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Long getFromVersionId() {
        return fromVersionId;
    }

    public void setFromVersionId(Long fromVersionId) {
        this.fromVersionId = fromVersionId;
    }

    public Long getToVersionId() {
        return toVersionId;
    }

    public void setToVersionId(Long toVersionId) {
        this.toVersionId = toVersionId;
    }

    public Long getTargetVersionId() {
        return targetVersionId;
    }

    public void setTargetVersionId(Long targetVersionId) {
        this.targetVersionId = targetVersionId;
    }

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Long getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Long draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getImpactReportJson() {
        return impactReportJson;
    }

    public void setImpactReportJson(String impactReportJson) {
        this.impactReportJson = impactReportJson;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(LocalDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }

    public Long getOperatedBy() {
        return operatedBy;
    }

    public void setOperatedBy(Long operatedBy) {
        this.operatedBy = operatedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
