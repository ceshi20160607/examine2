package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("msg_delivery")
public class MsgDelivery {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("account_id")
    private Long accountId;

    @TableField("`channel`")
    private String channel;

    @TableField("destination_masked")
    private String destinationMasked;

    @TableField("`status`")
    private String status;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @TableField("provider_message_id")
    private String providerMessageId;

    @TableField("provider_receipt_json")
    private String providerReceiptJson;

    @TableField("last_error")
    private String lastError;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDestinationMasked() {
        return destinationMasked;
    }

    public void setDestinationMasked(String destinationMasked) {
        this.destinationMasked = destinationMasked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getProviderReceiptJson() {
        return providerReceiptJson;
    }

    public void setProviderReceiptJson(String providerReceiptJson) {
        this.providerReceiptJson = providerReceiptJson;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "MsgDelivery{" +
            "id = " + id +
            ", messageId = " + messageId +
            ", accountId = " + accountId +
            ", channel = " + channel +
            ", destinationMasked = " + destinationMasked +
            ", status = " + status +
            ", attemptCount = " + attemptCount +
            ", nextAttemptAt = " + nextAttemptAt +
            ", providerMessageId = " + providerMessageId +
            ", providerReceiptJson = " + providerReceiptJson +
            ", lastError = " + lastError +
            ", sentAt = " + sentAt +
            ", deliveredAt = " + deliveredAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
