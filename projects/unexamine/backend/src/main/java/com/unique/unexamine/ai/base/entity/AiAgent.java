package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统 Agent 可编辑草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_agent")
public class AiAgent {

    /**
     * Agent 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Agent 范围：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统 Agent 所属系统，平台 Agent 为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统 Agent 所属租户，平台 Agent 为空
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 使用的平台模型标识
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 系统 Agent 使用的系统模型授权，平台 Agent 为空
     */
    @TableField("model_grant_id")
    private Long modelGrantId;

    /**
     * 当前范围内 Agent 编码
     */
    @TableField("`code`")
    private String code;

    /**
     * Agent 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * Agent 业务说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 可编辑草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 草稿系统提示词正文
     */
    @TableField("system_prompt_text")
    private String systemPromptText;

    /**
     * 草稿上下文和数据范围策略
     */
    @TableField("context_policy_json")
    private String contextPolicyJson;

    /**
     * 草稿人工确认策略
     */
    @TableField("confirmation_policy_json")
    private String confirmationPolicyJson;

    /**
     * 草稿模型失败降级策略
     */
    @TableField("fallback_policy_json")
    private String fallbackPolicyJson;

    /**
     * Agent 草稿或发布状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建平台账号标识
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 平台 Agent 创建人平台成员账号，系统 Agent 为空
     */
    @TableField("created_by_platform_member_account_id")
    private Long createdByPlatformMemberAccountId;

    /**
     * 系统 Agent 创建系统成员标识
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 草稿乐观锁版本
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
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

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public Long getCreatedByPlatformMemberAccountId() {
        return createdByPlatformMemberAccountId;
    }

    public void setCreatedByPlatformMemberAccountId(Long createdByPlatformMemberAccountId) {
        this.createdByPlatformMemberAccountId = createdByPlatformMemberAccountId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", modelId = " + modelId +
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
            ", createdByAccountId = " + createdByAccountId +
            ", createdByPlatformMemberAccountId = " + createdByPlatformMemberAccountId +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
