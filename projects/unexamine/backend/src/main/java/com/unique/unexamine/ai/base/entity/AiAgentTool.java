package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * Agent 工具及显式访问范围草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_agent_tool")
public class AiAgentTool {

    /**
     * Agent 工具主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Agent 标识
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 工具可用平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 工具所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统工具所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统工具所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 查询、建议或写入工具类别
     */
    @TableField("tool_type")
    private String toolType;

    /**
     * 可访问资源类别
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 可访问资源业务标识
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 授权动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 允许字段范围
     */
    @TableField("field_scope_json")
    private String fieldScopeJson;

    /**
     * 允许数据范围表达式
     */
    @TableField("data_scope_json")
    private String dataScopeJson;

    /**
     * 工具外发范围和开关
     */
    @TableField("outbound_policy_json")
    private String outboundPolicyJson;

    /**
     * 工具输入输出脱敏规则
     */
    @TableField("masking_policy_json")
    private String maskingPolicyJson;

    /**
     * 工具策略修订号
     */
    @TableField("policy_version")
    private Integer policyVersion;

    /**
     * 执行前是否必须人工确认
     */
    @TableField("requires_confirmation")
    private Boolean requiresConfirmation;

    /**
     * 工具启停状态
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

    public String getToolType() {
        return toolType;
    }

    public void setToolType(String toolType) {
        this.toolType = toolType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getFieldScopeJson() {
        return fieldScopeJson;
    }

    public void setFieldScopeJson(String fieldScopeJson) {
        this.fieldScopeJson = fieldScopeJson;
    }

    public String getDataScopeJson() {
        return dataScopeJson;
    }

    public void setDataScopeJson(String dataScopeJson) {
        this.dataScopeJson = dataScopeJson;
    }

    public String getOutboundPolicyJson() {
        return outboundPolicyJson;
    }

    public void setOutboundPolicyJson(String outboundPolicyJson) {
        this.outboundPolicyJson = outboundPolicyJson;
    }

    public String getMaskingPolicyJson() {
        return maskingPolicyJson;
    }

    public void setMaskingPolicyJson(String maskingPolicyJson) {
        this.maskingPolicyJson = maskingPolicyJson;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer policyVersion) {
        this.policyVersion = policyVersion;
    }

    public Boolean getRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(Boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
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
        return "AiAgentTool{" +
            "id = " + id +
            ", agentId = " + agentId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", toolType = " + toolType +
            ", resourceType = " + resourceType +
            ", resourceId = " + resourceId +
            ", actionCode = " + actionCode +
            ", fieldScopeJson = " + fieldScopeJson +
            ", dataScopeJson = " + dataScopeJson +
            ", outboundPolicyJson = " + outboundPolicyJson +
            ", maskingPolicyJson = " + maskingPolicyJson +
            ", policyVersion = " + policyVersion +
            ", requiresConfirmation = " + requiresConfirmation +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
