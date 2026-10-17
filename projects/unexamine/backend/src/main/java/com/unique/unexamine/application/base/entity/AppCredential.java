package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用访问凭证与密钥版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_credential")
public class AppCredential {

    /**
     * 凭证主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 应用内递增的凭证版本号
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 公开的应用访问标识
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 凭证密钥的不可逆摘要
     */
    @TableField("secret_hash")
    private String secretHash;

    /**
     * 签名材料的受控密钥引用，不存明文
     */
    @TableField("signing_secret_ref")
    private String signingSecretRef;

    /**
     * 仅供识别的脱敏尾号
     */
    @TableField("secret_hint")
    private String secretHint;

    /**
     * 凭证生效时间
     */
    @TableField("valid_start_time")
    private LocalDateTime validStartTime;

    /**
     * 凭证到期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 凭证状态：ACTIVE启用或REVOKED撤销
     */
    @TableField("`status`")
    private String status;

    /**
     * 凭证撤销时间
     */
    @TableField("revoke_time")
    private LocalDateTime revokeTime;

    /**
     * 创建凭证的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSigningSecretRef() {
        return signingSecretRef;
    }

    public void setSigningSecretRef(String signingSecretRef) {
        this.signingSecretRef = signingSecretRef;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public void setSecretHint(String secretHint) {
        this.secretHint = secretHint;
    }

    public LocalDateTime getValidStartTime() {
        return validStartTime;
    }

    public void setValidStartTime(LocalDateTime validStartTime) {
        this.validStartTime = validStartTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRevokeTime() {
        return revokeTime;
    }

    public void setRevokeTime(LocalDateTime revokeTime) {
        this.revokeTime = revokeTime;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AppCredential{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", credentialVersion = " + credentialVersion +
            ", clientId = " + clientId +
            ", secretHash = " + secretHash +
            ", signingSecretRef = " + signingSecretRef +
            ", secretHint = " + secretHint +
            ", validStartTime = " + validStartTime +
            ", expireTime = " + expireTime +
            ", status = " + status +
            ", revokeTime = " + revokeTime +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
