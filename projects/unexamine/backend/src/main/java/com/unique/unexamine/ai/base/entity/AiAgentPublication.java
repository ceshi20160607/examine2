package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * Agent 当前生效发布版本指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_agent_publication")
public class AiAgentPublication {

    /**
     * Agent 发布指针主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Agent 标识
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 发布指针平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统发布指针所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统发布指针所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 当前生效 Agent 版本标识
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 更新发布指针的平台账号标识
     */
    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    /**
     * 平台发布指针更新人成员账号，系统更新为空
     */
    @TableField("updated_by_platform_member_account_id")
    private Long updatedByPlatformMemberAccountId;

    /**
     * 系统发布指针更新成员标识
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 发布指针乐观锁版本
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

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
    }

    public Long getUpdatedByPlatformMemberAccountId() {
        return updatedByPlatformMemberAccountId;
    }

    public void setUpdatedByPlatformMemberAccountId(Long updatedByPlatformMemberAccountId) {
        this.updatedByPlatformMemberAccountId = updatedByPlatformMemberAccountId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
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
        return "AiAgentPublication{" +
            "id = " + id +
            ", agentId = " + agentId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByAccountId = " + updatedByAccountId +
            ", updatedByPlatformMemberAccountId = " + updatedByPlatformMemberAccountId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
