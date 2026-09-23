package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台消息投递扩展
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_provider_delivery")
public class AppProviderDelivery {

    /**
     * 外部平台消息投递扩展主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属外部平台连接主键
     */
    @TableField("connection_id")
    private Long connectionId;

    /**
     * 目标外部平台成员映射主键
     */
    @TableField("identity_id")
    private Long identityId;

    /**
     * 统一消息主键
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 统一消息投递主键
     */
    @TableField("message_delivery_id")
    private Long messageDeliveryId;

    /**
     * 同一连接内的业务幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 规范化投递请求SHA-256摘要
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 按当前上下文生成的站内业务深链
     */
    @TableField("deep_link")
    private String deepLink;

    /**
     * 投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销
     */
    @TableField("`status`")
    private String status;

    /**
     * 平台返回的消息标识
     */
    @TableField("provider_message_id")
    private String providerMessageId;

    /**
     * 不含敏感信息的最近错误编码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 发起人平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

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
     * 实际发送时间
     */
    @TableField("sent_time")
    private LocalDateTime sentTime;

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

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public void setIdentityId(Long identityId) {
        this.identityId = identityId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getMessageDeliveryId() {
        return messageDeliveryId;
    }

    public void setMessageDeliveryId(Long messageDeliveryId) {
        this.messageDeliveryId = messageDeliveryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public void setDeepLink(String deepLink) {
        this.deepLink = deepLink;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getSentTime() {
        return sentTime;
    }

    public void setSentTime(LocalDateTime sentTime) {
        this.sentTime = sentTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppProviderDelivery{" +
            "id = " + id +
            ", connectionId = " + connectionId +
            ", identityId = " + identityId +
            ", messageId = " + messageId +
            ", messageDeliveryId = " + messageDeliveryId +
            ", idempotencyKey = " + idempotencyKey +
            ", requestHash = " + requestHash +
            ", deepLink = " + deepLink +
            ", status = " + status +
            ", providerMessageId = " + providerMessageId +
            ", errorCode = " + errorCode +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", sentTime = " + sentTime +
            ", version = " + version +
            "}";
    }
}
