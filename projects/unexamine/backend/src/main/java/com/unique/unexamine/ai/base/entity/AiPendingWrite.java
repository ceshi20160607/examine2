package com.unique.unexamine.ai.base.entity;

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
@TableName("ai_pending_write")
public class AiPendingWrite {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private Long executionId;

    @TableField("step_id")
    private Long stepId;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("proposed_payload_json")
    private String proposedPayloadJson;

    @TableField("preview_json")
    private String previewJson;

    @TableField("`status`")
    private String status;

    @TableField("confirmed_by_account_id")
    private Long confirmedByAccountId;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getProposedPayloadJson() {
        return proposedPayloadJson;
    }

    public void setProposedPayloadJson(String proposedPayloadJson) {
        this.proposedPayloadJson = proposedPayloadJson;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public void setPreviewJson(String previewJson) {
        this.previewJson = previewJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfirmedByAccountId() {
        return confirmedByAccountId;
    }

    public void setConfirmedByAccountId(Long confirmedByAccountId) {
        this.confirmedByAccountId = confirmedByAccountId;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AiPendingWrite{" +
            "id = " + id +
            ", executionId = " + executionId +
            ", stepId = " + stepId +
            ", targetType = " + targetType +
            ", targetId = " + targetId +
            ", proposedPayloadJson = " + proposedPayloadJson +
            ", previewJson = " + previewJson +
            ", status = " + status +
            ", confirmedByAccountId = " + confirmedByAccountId +
            ", confirmedAt = " + confirmedAt +
            ", resultJson = " + resultJson +
            ", errorMessage = " + errorMessage +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
