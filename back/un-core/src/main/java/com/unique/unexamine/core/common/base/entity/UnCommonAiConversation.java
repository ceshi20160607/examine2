package com.unique.unexamine.core.common.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 平台与系统共用的智能对话追溯记录，原文只经作用域授权接口读取。 */
@TableName("un_common_ai_conversation")
public class UnCommonAiConversation implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 智能对话记录主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 跨预览确认和业务日志的追踪标识。 */
    @TableField("trace_id")
    private String traceId;

    /** 平台或系统智能对话作用域。 */
    @TableField("scope_type")
    private String scopeType;

    /** 系统作用域的业务系统主键。 */
    @TableField("system_id")
    private Long systemId;

    /** 系统作用域的租户主键。 */
    @TableField("tenant_id")
    private Long tenantId;

    /** 发起对话的平台账号主键。 */
    @TableField("account_id")
    private Long accountId;

    /** 所使用的系统或平台智能配置主键。 */
    @TableField("config_id")
    private Long configId;

    /** 实际调用的平台模型配置主键。 */
    @TableField("platform_model_id")
    private Long platformModelId;

    /** 系统 Agent 发布策略版本。 */
    @TableField("system_policy_version")
    private Integer systemPolicyVersion;

    /** 平台模型发布版本。 */
    @TableField("platform_model_version")
    private Integer platformModelVersion;

    /** 系统提示词版本。 */
    @TableField("prompt_version")
    private String promptVersion;

    /** 模型识别的业务意图编码。 */
    @TableField("intent_code")
    private String intentCode;

    /** 用户原始对话内容。 */
    @TableField("request_text")
    private String requestText;

    /** 模型生成的结构化预览。 */
    @TableField("proposal_json")
    private String proposalJson;

    /** 预览确认或失败状态。 */
    @TableField("status")
    private String status;

    /** 确认后生成的业务对象类型。 */
    @TableField("target_type")
    private String targetType;

    /** 确认后生成的业务对象主键。 */
    @TableField("target_id")
    private Long targetId;

    /** 实际确认的平台账号主键。 */
    @TableField("confirm_account_id")
    private Long confirmAccountId;

    /** 用户确认时间。 */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /** 原始对话保留到期时间。 */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 原始对话创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最近状态更新时间。 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除时间。 */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public Long getPlatformModelId() { return platformModelId; }
    public void setPlatformModelId(Long platformModelId) { this.platformModelId = platformModelId; }

    public Integer getSystemPolicyVersion() { return systemPolicyVersion; }
    public void setSystemPolicyVersion(Integer systemPolicyVersion) { this.systemPolicyVersion = systemPolicyVersion; }

    public Integer getPlatformModelVersion() { return platformModelVersion; }
    public void setPlatformModelVersion(Integer platformModelVersion) { this.platformModelVersion = platformModelVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getIntentCode() { return intentCode; }
    public void setIntentCode(String intentCode) { this.intentCode = intentCode; }

    public String getRequestText() { return requestText; }
    public void setRequestText(String requestText) { this.requestText = requestText; }

    public String getProposalJson() { return proposalJson; }
    public void setProposalJson(String proposalJson) { this.proposalJson = proposalJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Long getConfirmAccountId() { return confirmAccountId; }
    public void setConfirmAccountId(Long confirmAccountId) { this.confirmAccountId = confirmAccountId; }

    public LocalDateTime getConfirmTime() { return confirmTime; }
    public void setConfirmTime(LocalDateTime confirmTime) { this.confirmTime = confirmTime; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public LocalDateTime getDeletedTime() { return deletedTime; }
    public void setDeletedTime(LocalDateTime deletedTime) { this.deletedTime = deletedTime; }
}
