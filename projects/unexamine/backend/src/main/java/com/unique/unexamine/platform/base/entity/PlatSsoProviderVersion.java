package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台身份源的测试与发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_sso_provider_version")
public class PlatSsoProviderVersion {

    /**
     * 身份源版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台身份源标识
     */
    @TableField("provider_id")
    private Long providerId;

    /**
     * 身份源内递增的版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * OIDC、SAML、OAuth2、LDAP 或企业身份协议
     */
    @TableField("protocol")
    private String protocol;

    /**
     * 身份源发行者标识
     */
    @TableField("`issuer`")
    private String issuer;

    /**
     * 身份源客户端标识
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 客户端密钥的受控引用
     */
    @TableField("client_secret_ref")
    private String clientSecretRef;

    /**
     * 协议连接与证书配置快照
     */
    @TableField("protocol_config_json")
    private String protocolConfigJson;

    /**
     * 允许登录的域名白名单
     */
    @TableField("allowed_domains_json")
    private String allowedDomainsJson;

    /**
     * 外部身份属性映射快照
     */
    @TableField("attribute_mapping_json")
    private String attributeMappingJson;

    /**
     * 即时建号与成员策略快照
     */
    @TableField("jit_policy_json")
    private String jitPolicyJson;

    /**
     * 多因素认证策略快照
     */
    @TableField("mfa_policy_json")
    private String mfaPolicyJson;

    /**
     * 允许的回调地址快照
     */
    @TableField("callback_uris_json")
    private String callbackUrisJson;

    /**
     * 连接测试状态
     */
    @TableField("test_status")
    private String testStatus;

    /**
     * 连接测试与预演报告
     */
    @TableField("test_report_json")
    private String testReportJson;

    /**
     * 最近一次测试完成时间
     */
    @TableField("tested_time")
    private LocalDateTime testedTime;

    /**
     * 最近一次测试操作账号
     */
    @TableField("tested_by_account_id")
    private Long testedByAccountId;

    /**
     * 草稿、发布或归档状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 版本发布时间
     */
    @TableField("published_time")
    private LocalDateTime publishedTime;

    /**
     * 发布操作账号
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 版本创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretRef() {
        return clientSecretRef;
    }

    public void setClientSecretRef(String clientSecretRef) {
        this.clientSecretRef = clientSecretRef;
    }

    public String getProtocolConfigJson() {
        return protocolConfigJson;
    }

    public void setProtocolConfigJson(String protocolConfigJson) {
        this.protocolConfigJson = protocolConfigJson;
    }

    public String getAllowedDomainsJson() {
        return allowedDomainsJson;
    }

    public void setAllowedDomainsJson(String allowedDomainsJson) {
        this.allowedDomainsJson = allowedDomainsJson;
    }

    public String getAttributeMappingJson() {
        return attributeMappingJson;
    }

    public void setAttributeMappingJson(String attributeMappingJson) {
        this.attributeMappingJson = attributeMappingJson;
    }

    public String getJitPolicyJson() {
        return jitPolicyJson;
    }

    public void setJitPolicyJson(String jitPolicyJson) {
        this.jitPolicyJson = jitPolicyJson;
    }

    public String getMfaPolicyJson() {
        return mfaPolicyJson;
    }

    public void setMfaPolicyJson(String mfaPolicyJson) {
        this.mfaPolicyJson = mfaPolicyJson;
    }

    public String getCallbackUrisJson() {
        return callbackUrisJson;
    }

    public void setCallbackUrisJson(String callbackUrisJson) {
        this.callbackUrisJson = callbackUrisJson;
    }

    public String getTestStatus() {
        return testStatus;
    }

    public void setTestStatus(String testStatus) {
        this.testStatus = testStatus;
    }

    public String getTestReportJson() {
        return testReportJson;
    }

    public void setTestReportJson(String testReportJson) {
        this.testReportJson = testReportJson;
    }

    public LocalDateTime getTestedTime() {
        return testedTime;
    }

    public void setTestedTime(LocalDateTime testedTime) {
        this.testedTime = testedTime;
    }

    public Long getTestedByAccountId() {
        return testedByAccountId;
    }

    public void setTestedByAccountId(Long testedByAccountId) {
        this.testedByAccountId = testedByAccountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(LocalDateTime publishedTime) {
        this.publishedTime = publishedTime;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "PlatSsoProviderVersion{" +
            "id = " + id +
            ", providerId = " + providerId +
            ", versionNumber = " + versionNumber +
            ", protocol = " + protocol +
            ", issuer = " + issuer +
            ", clientId = " + clientId +
            ", clientSecretRef = " + clientSecretRef +
            ", protocolConfigJson = " + protocolConfigJson +
            ", allowedDomainsJson = " + allowedDomainsJson +
            ", attributeMappingJson = " + attributeMappingJson +
            ", jitPolicyJson = " + jitPolicyJson +
            ", mfaPolicyJson = " + mfaPolicyJson +
            ", callbackUrisJson = " + callbackUrisJson +
            ", testStatus = " + testStatus +
            ", testReportJson = " + testReportJson +
            ", testedTime = " + testedTime +
            ", testedByAccountId = " + testedByAccountId +
            ", status = " + status +
            ", publishedTime = " + publishedTime +
            ", publishedByAccountId = " + publishedByAccountId +
            ", createdTime = " + createdTime +
            "}";
    }
}
