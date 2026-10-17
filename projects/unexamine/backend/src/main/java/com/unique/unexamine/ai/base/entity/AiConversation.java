package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 固定 Agent 版本与成员上下文的 AI 对话
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_conversation")
public class AiConversation {

    /**
     * AI 对话主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对话平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 对话所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统对话所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统对话所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 对话使用 Agent 标识
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 对话固定已发布 Agent 版本标识
     */
    @TableField("agent_version_id")
    private Long agentVersionId;

    /**
     * 对话发起平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 平台对话发起人成员账号，系统对话为空
     */
    @TableField("platform_member_account_id")
    private Long platformMemberAccountId;

    /**
     * 系统对话发起系统成员标识
     */
    @TableField("system_member_id")
    private Long systemMemberId;

    /**
     * 入口场景与来源信息快照
     */
    @TableField("entry_context_json")
    private String entryContextJson;

    /**
     * 对话标题
     */
    @TableField("title")
    private String title;

    /**
     * 对话状态
     */
    @TableField("`status`")
    private String status;

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
     * 乐观锁版本
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

    public Long getAgentVersionId() {
        return agentVersionId;
    }

    public void setAgentVersionId(Long agentVersionId) {
        this.agentVersionId = agentVersionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getPlatformMemberAccountId() {
        return platformMemberAccountId;
    }

    public void setPlatformMemberAccountId(Long platformMemberAccountId) {
        this.platformMemberAccountId = platformMemberAccountId;
    }

    public Long getSystemMemberId() {
        return systemMemberId;
    }

    public void setSystemMemberId(Long systemMemberId) {
        this.systemMemberId = systemMemberId;
    }

    public String getEntryContextJson() {
        return entryContextJson;
    }

    public void setEntryContextJson(String entryContextJson) {
        this.entryContextJson = entryContextJson;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        return "AiConversation{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", agentId = " + agentId +
            ", agentVersionId = " + agentVersionId +
            ", accountId = " + accountId +
            ", platformMemberAccountId = " + platformMemberAccountId +
            ", systemMemberId = " + systemMemberId +
            ", entryContextJson = " + entryContextJson +
            ", title = " + title +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
