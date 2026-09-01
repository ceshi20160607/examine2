package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("plat_sso_provider_version")
public class PlatSsoProviderVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("provider_id")
    private Long providerId;

    @TableField("version_number")
    private Integer versionNumber;

    @TableField("protocol")
    private String protocol;

    @TableField("`issuer`")
    private String issuer;

    @TableField("client_id")
    private String clientId;

    @TableField("client_secret_ref")
    private String clientSecretRef;

    @TableField("protocol_config_json")
    private String protocolConfigJson;

    @TableField("allowed_domains_json")
    private String allowedDomainsJson;

    @TableField("attribute_mapping_json")
    private String attributeMappingJson;

    @TableField("jit_policy_json")
    private String jitPolicyJson;

    @TableField("mfa_policy_json")
    private String mfaPolicyJson;

    @TableField("callback_uris_json")
    private String callbackUrisJson;

    @TableField("test_status")
    private String testStatus;

    @TableField("test_report_json")
    private String testReportJson;

    @TableField("tested_at")
    private LocalDateTime testedAt;

    @TableField("tested_by_account_id")
    private Long testedByAccountId;

    @TableField("`status`")
    private String status;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getTestedAt() {
        return testedAt;
    }

    public void setTestedAt(LocalDateTime testedAt) {
        this.testedAt = testedAt;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
            ", testedAt = " + testedAt +
            ", testedByAccountId = " + testedByAccountId +
            ", status = " + status +
            ", publishedAt = " + publishedAt +
            ", publishedByAccountId = " + publishedByAccountId +
            ", createdAt = " + createdAt +
            "}";
    }
}
