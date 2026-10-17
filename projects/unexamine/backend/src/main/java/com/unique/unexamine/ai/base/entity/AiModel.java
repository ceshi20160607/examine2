package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台模型与受控密钥引用配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_model")
public class AiModel {

    /**
     * 模型主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模型所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 模型配置归属层级，PLATFORM 或 SYSTEM
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 平台模型为平台主键，系统模型为系统主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 系统自建模型所属系统主键，平台模型为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 平台内模型编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 模型显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 模型供应商
     */
    @TableField("provider")
    private String provider;

    /**
     * 供应商模型名称
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 模型接口地址
     */
    @TableField("endpoint_url")
    private String endpointUrl;

    /**
     * 受控密钥引用码，不保存密钥明文；V18 建表后绑定密钥引用
     */
    @TableField("credential_ref")
    private String credentialRef;

    /**
     * 平台受控密钥引用主键
     */
    @TableField("credential_ref_id")
    private Long credentialRefId;

    /**
     * 模型密钥固定为平台范围
     */
    @TableField("credential_scope_type")
    private String credentialScopeType;

    /**
     * 当前密钥引用版本
     */
    @TableField("credential_version")
    private String credentialVersion;

    /**
     * 模型可用能力配置
     */
    @TableField("capabilities_json")
    private String capabilitiesJson;

    /**
     * 额度和并发限制配置
     */
    @TableField("limit_policy_json")
    private String limitPolicyJson;

    /**
     * 外发及数据出境策略
     */
    @TableField("outbound_policy_json")
    private String outboundPolicyJson;

    /**
     * 发送模型前的脱敏策略
     */
    @TableField("masking_policy_json")
    private String maskingPolicyJson;

    /**
     * 模型启停状态
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
     * 模型配置乐观锁版本
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public Long getCredentialRefId() {
        return credentialRefId;
    }

    public void setCredentialRefId(Long credentialRefId) {
        this.credentialRefId = credentialRefId;
    }

    public String getCredentialScopeType() {
        return credentialScopeType;
    }

    public void setCredentialScopeType(String credentialScopeType) {
        this.credentialScopeType = credentialScopeType;
    }

    public String getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(String credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public String getLimitPolicyJson() {
        return limitPolicyJson;
    }

    public void setLimitPolicyJson(String limitPolicyJson) {
        this.limitPolicyJson = limitPolicyJson;
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
        return "AiModel{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", systemId = " + systemId +
            ", code = " + code +
            ", name = " + name +
            ", provider = " + provider +
            ", modelName = " + modelName +
            ", endpointUrl = " + endpointUrl +
            ", credentialRef = " + credentialRef +
            ", credentialRefId = " + credentialRefId +
            ", credentialScopeType = " + credentialScopeType +
            ", credentialVersion = " + credentialVersion +
            ", capabilitiesJson = " + capabilitiesJson +
            ", limitPolicyJson = " + limitPolicyJson +
            ", outboundPolicyJson = " + outboundPolicyJson +
            ", maskingPolicyJson = " + maskingPolicyJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
