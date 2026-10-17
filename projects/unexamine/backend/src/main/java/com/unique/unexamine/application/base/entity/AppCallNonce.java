package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 已接受应用签名nonce的防重放记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_call_nonce")
public class AppCallNonce {

    /**
     * 已接受签名nonce主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 目标应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 验签所用应用凭证版本号
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 请求防重放随机值
     */
    @TableField("nonce")
    private String nonce;

    /**
     * 首次接受该nonce的请求标识
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 调用方签名请求时间
     */
    @TableField("request_time")
    private LocalDateTime requestTime;

    /**
     * nonce失效时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 记录创建时间
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

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AppCallNonce{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", credentialVersion = " + credentialVersion +
            ", nonce = " + nonce +
            ", requestId = " + requestId +
            ", requestTime = " + requestTime +
            ", expireTime = " + expireTime +
            ", createTime = " + createTime +
            "}";
    }
}
