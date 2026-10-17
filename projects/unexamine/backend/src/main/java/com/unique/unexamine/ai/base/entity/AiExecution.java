package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 固定模型授权与权限策略快照的 AI 执行
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_execution")
public class AiExecution {

    /**
     * AI 执行主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属对话标识
     */
    @TableField("conversation_id")
    private Long conversationId;

    /**
     * 执行平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 执行所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统执行所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统执行所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 实际执行 Agent 标识
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 触发本次执行的用户消息标识
     */
    @TableField("request_message_id")
    private Long requestMessageId;

    /**
     * 执行固定不可变 Agent 版本标识
     */
    @TableField("agent_version_id")
    private Long agentVersionId;

    /**
     * 执行固定模型标识
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 执行固定模型配置版本
     */
    @TableField("model_config_version")
    private Integer modelConfigVersion;

    /**
     * 系统执行固定模型授权标识
     */
    @TableField("model_grant_id")
    private Long modelGrantId;

    /**
     * 系统执行固定模型授权发布版本标识
     */
    @TableField("model_grant_version_id")
    private Long modelGrantVersionId;

    /**
     * 执行使用的受控密钥引用码
     */
    @TableField("credential_ref")
    private String credentialRef;

    /**
     * 执行使用的密钥版本
     */
    @TableField("credential_version")
    private String credentialVersion;

    /**
     * 执行使用的 Agent 策略版本
     */
    @TableField("policy_version")
    private Integer policyVersion;

    /**
     * 当前角色、字段、数据范围与应用授权快照
     */
    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    /**
     * 执行时外发、脱敏与确认策略快照
     */
    @TableField("policy_snapshot_json")
    private String policySnapshotJson;

    /**
     * 执行状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 是否进入失败降级路径
     */
    @TableField("fallback_used")
    private Boolean fallbackUsed;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 执行失败错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 执行失败摘要，不含密钥明文
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 执行状态乐观锁版本
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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Integer getModelConfigVersion() {
        return modelConfigVersion;
    }

    public void setModelConfigVersion(Integer modelConfigVersion) {
        this.modelConfigVersion = modelConfigVersion;
    }

    public Long getModelGrantId() {
        return modelGrantId;
    }

    public void setModelGrantId(Long modelGrantId) {
        this.modelGrantId = modelGrantId;
    }

    public Long getModelGrantVersionId() {
        return modelGrantVersionId;
    }

    public void setModelGrantVersionId(Long modelGrantVersionId) {
        this.modelGrantVersionId = modelGrantVersionId;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(String credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getPolicySnapshotJson() {
        return policySnapshotJson;
    }

    public void setPolicySnapshotJson(String policySnapshotJson) {
        this.policySnapshotJson = policySnapshotJson;
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
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", agentId = " + agentId +
            ", requestMessageId = " + requestMessageId +
            ", agentVersionId = " + agentVersionId +
            ", modelId = " + modelId +
            ", modelConfigVersion = " + modelConfigVersion +
            ", modelGrantId = " + modelGrantId +
            ", modelGrantVersionId = " + modelGrantVersionId +
            ", credentialRef = " + credentialRef +
            ", credentialVersion = " + credentialVersion +
            ", policyVersion = " + policyVersion +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", policySnapshotJson = " + policySnapshotJson +
            ", status = " + status +
            ", fallbackUsed = " + fallbackUsed +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", version = " + version +
            "}";
    }
}
