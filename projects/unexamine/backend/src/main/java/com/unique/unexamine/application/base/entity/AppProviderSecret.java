package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台密钥版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_provider_secret")
public class AppProviderSecret {

    /**
     * 外部平台密钥版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属外部平台连接主键
     */
    @TableField("connection_id")
    private Long connectionId;

    /**
     * 密钥用途：API_SECRET接口密钥、CALLBACK_TOKEN回调令牌、ENCODING_AES_KEY回调加密密钥
     */
    @TableField("secret_type")
    private String secretType;

    /**
     * 同一连接同一用途的递增版本号
     */
    @TableField("secret_version")
    private Integer secretVersion;

    /**
     * 使用应用主密钥加密后的密文
     */
    @TableField("secret_ciphertext")
    private String secretCiphertext;

    /**
     * 仅用于辨认版本的脱敏尾号
     */
    @TableField("secret_hint")
    private String secretHint;

    /**
     * 密钥状态：ACTIVE启用、REVOKED已撤销
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建人平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 撤销时间
     */
    @TableField("revoked_time")
    private LocalDateTime revokedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getSecretType() {
        return secretType;
    }

    public void setSecretType(String secretType) {
        this.secretType = secretType;
    }

    public Integer getSecretVersion() {
        return secretVersion;
    }

    public void setSecretVersion(Integer secretVersion) {
        this.secretVersion = secretVersion;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public void setSecretHint(String secretHint) {
        this.secretHint = secretHint;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getRevokedTime() {
        return revokedTime;
    }

    public void setRevokedTime(LocalDateTime revokedTime) {
        this.revokedTime = revokedTime;
    }

    @Override
    public String toString() {
        return "AppProviderSecret{" +
            "id = " + id +
            ", connectionId = " + connectionId +
            ", secretType = " + secretType +
            ", secretVersion = " + secretVersion +
            ", secretCiphertext = " + secretCiphertext +
            ", secretHint = " + secretHint +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createdTime = " + createdTime +
            ", revokedTime = " + revokedTime +
            "}";
    }
}
