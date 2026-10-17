package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统消息渠道投递及失败重试记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_msg_delivery")
public class MsgDelivery {

    /**
     * 系统消息渠道投递主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统消息主键
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 投递目标账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 投递渠道
     */
    @TableField("`channel`")
    private String channel;

    /**
     * 脱敏后的投递目的地
     */
    @TableField("destination_masked")
    private String destinationMasked;

    /**
     * 投递状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 已执行的投递次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 下一次允许重试时间
     */
    @TableField("next_attempt_time")
    private LocalDateTime nextAttemptTime;

    /**
     * 外部渠道返回的消息标识
     */
    @TableField("provider_message_id")
    private String providerMessageId;

    /**
     * 外部渠道投递回执
     */
    @TableField("provider_receipt_json")
    private String providerReceiptJson;

    /**
     * 最近一次失败摘要，不含密钥
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 发送成功时间
     */
    @TableField("send_time")
    private LocalDateTime sendTime;

    /**
     * 渠道确认送达时间
     */
    @TableField("deliver_time")
    private LocalDateTime deliverTime;

    /**
     * 投递记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 投递记录更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
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

    public LocalDateTime getNextAttemptTime() {
        return nextAttemptTime;
    }

    public void setNextAttemptTime(LocalDateTime nextAttemptTime) {
        this.nextAttemptTime = nextAttemptTime;
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

    public LocalDateTime getSendTime() {
        return sendTime;
    }

    public void setSendTime(LocalDateTime sendTime) {
        this.sendTime = sendTime;
    }

    public LocalDateTime getDeliverTime() {
        return deliverTime;
    }

    public void setDeliverTime(LocalDateTime deliverTime) {
        this.deliverTime = deliverTime;
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
        return "MsgDelivery{" +
            "id = " + id +
            ", messageId = " + messageId +
            ", accountId = " + accountId +
            ", channel = " + channel +
            ", destinationMasked = " + destinationMasked +
            ", status = " + status +
            ", attemptCount = " + attemptCount +
            ", nextAttemptTime = " + nextAttemptTime +
            ", providerMessageId = " + providerMessageId +
            ", providerReceiptJson = " + providerReceiptJson +
            ", lastError = " + lastError +
            ", sendTime = " + sendTime +
            ", deliverTime = " + deliverTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
