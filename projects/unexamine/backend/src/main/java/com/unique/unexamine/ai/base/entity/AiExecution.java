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
@TableName("ai_execution")
public class AiExecution {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("request_message_id")
    private Long requestMessageId;

    @TableField("agent_version_id")
    private Long agentVersionId;

    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    @TableField("`status`")
    private String status;

    @TableField("fallback_used")
    private Boolean fallbackUsed;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getRequestMessageId() {
        return requestMessageId;
    }

    public void setRequestMessageId(Long requestMessageId) {
        this.requestMessageId = requestMessageId;
    }

    public Long getAgentVersionId() {
        return agentVersionId;
    }

    public void setAgentVersionId(Long agentVersionId) {
        this.agentVersionId = agentVersionId;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(Boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AiExecution{" +
            "id = " + id +
            ", conversationId = " + conversationId +
            ", requestMessageId = " + requestMessageId +
            ", agentVersionId = " + agentVersionId +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", status = " + status +
            ", fallbackUsed = " + fallbackUsed +
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", version = " + version +
            "}";
    }
}
