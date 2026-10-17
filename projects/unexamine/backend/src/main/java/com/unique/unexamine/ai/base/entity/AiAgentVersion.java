package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变 Agent 发布配置、模型及策略快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_agent_version")
public class AiAgentVersion {

    /**
     * Agent 不可变发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Agent 标识
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 发布版本平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 发布版本所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统发布版本所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统发布版本所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 发布时固定模型标识
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 系统发布时固定模型授权标识
     */
    @TableField("model_grant_id")
    private Long modelGrantId;

    /**
     * 系统发布时固定不可变授权版本标识
     */
    @TableField("model_grant_version_id")
    private Long modelGrantVersionId;

    /**
     * 发布时模型配置版本
     */
    @TableField("model_config_version")
    private Integer modelConfigVersion;

    /**
     * 发布时使用的受控密钥引用码
     */
    @TableField("credential_ref")
    private String credentialRef;

    /**
     * 发布时使用的密钥版本
     */
    @TableField("credential_version")
    private String credentialVersion;

    /**
     * Agent 权限、外发和脱敏策略版本
     */
    @TableField("policy_version")
    private Integer policyVersion;

    /**
     * Agent 发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 被发布的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 不可变配置快照摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 提示词、工具及业务配置完整快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 模块、字段、动作、数据范围及外发脱敏策略快照
     */
    @TableField("policy_snapshot_json")
    private String policySnapshotJson;

    /**
     * 发布平台账号标识
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 平台发布者成员账号，系统发布为空
     */
    @TableField("published_by_platform_member_account_id")
    private Long publishedByPlatformMemberAccountId;

    /**
     * 系统发布成员标识
     */
    @TableField("published_by_member_id")
    private Long publishedByMemberId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public Long getModelGrantVersionId() {
        return modelGrantVersionId;
    }

    public void setModelGrantVersionId(Long modelGrantVersionId) {
        this.modelGrantVersionId = modelGrantVersionId;
    }

    public Integer getModelConfigVersion() {
        return modelConfigVersion;
    }

    public void setModelConfigVersion(Integer modelConfigVersion) {
        this.modelConfigVersion = modelConfigVersion;
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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getPolicySnapshotJson() {
        return policySnapshotJson;
    }

    public void setPolicySnapshotJson(String policySnapshotJson) {
        this.policySnapshotJson = policySnapshotJson;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public Long getPublishedByPlatformMemberAccountId() {
        return publishedByPlatformMemberAccountId;
    }

    public void setPublishedByPlatformMemberAccountId(Long publishedByPlatformMemberAccountId) {
        this.publishedByPlatformMemberAccountId = publishedByPlatformMemberAccountId;
    }

    public Long getPublishedByMemberId() {
        return publishedByMemberId;
    }

    public void setPublishedByMemberId(Long publishedByMemberId) {
        this.publishedByMemberId = publishedByMemberId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "AiAgentVersion{" +
            "id = " + id +
            ", agentId = " + agentId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", modelId = " + modelId +
            ", modelGrantId = " + modelGrantId +
            ", modelGrantVersionId = " + modelGrantVersionId +
            ", modelConfigVersion = " + modelConfigVersion +
            ", credentialRef = " + credentialRef +
            ", credentialVersion = " + credentialVersion +
            ", policyVersion = " + policyVersion +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotHash = " + snapshotHash +
            ", snapshotJson = " + snapshotJson +
            ", policySnapshotJson = " + policySnapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishedByPlatformMemberAccountId = " + publishedByPlatformMemberAccountId +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishTime = " + publishTime +
            "}";
    }
}
