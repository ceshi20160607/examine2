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
@TableName("ai_agent")
public class AiAgent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    @TableField("model_grant_id")
    private Long modelGrantId;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("`description`")
    private String description;

    @TableField("draft_revision")
    private Integer draftRevision;

    @TableField("system_prompt_text")
    private String systemPromptText;

    @TableField("context_policy_json")
    private String contextPolicyJson;

    @TableField("confirmation_policy_json")
    private String confirmationPolicyJson;

    @TableField("fallback_policy_json")
    private String fallbackPolicyJson;

    @TableField("`status`")
    private String status;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModelGrantId() {
        return modelGrantId;
    }

    public void setModelGrantId(Long modelGrantId) {
        this.modelGrantId = modelGrantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getSystemPromptText() {
        return systemPromptText;
    }

    public void setSystemPromptText(String systemPromptText) {
        this.systemPromptText = systemPromptText;
    }

    public String getContextPolicyJson() {
        return contextPolicyJson;
    }

    public void setContextPolicyJson(String contextPolicyJson) {
        this.contextPolicyJson = contextPolicyJson;
    }

    public String getConfirmationPolicyJson() {
        return confirmationPolicyJson;
    }

    public void setConfirmationPolicyJson(String confirmationPolicyJson) {
        this.confirmationPolicyJson = confirmationPolicyJson;
    }

    public String getFallbackPolicyJson() {
        return fallbackPolicyJson;
    }

    public void setFallbackPolicyJson(String fallbackPolicyJson) {
        this.fallbackPolicyJson = fallbackPolicyJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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
        return "AiAgent{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", modelGrantId = " + modelGrantId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", draftRevision = " + draftRevision +
            ", systemPromptText = " + systemPromptText +
            ", contextPolicyJson = " + contextPolicyJson +
            ", confirmationPolicyJson = " + confirmationPolicyJson +
            ", fallbackPolicyJson = " + fallbackPolicyJson +
            ", status = " + status +
            ", createdByMemberId = " + createdByMemberId +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
