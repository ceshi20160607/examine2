package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统消息成员收件箱及回执
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_msg_recipient")
public class MsgRecipient {

    /**
     * 系统消息接收记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统消息主键
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 接收人的平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 接收人在系统中的成员主键
     */
    @TableField("system_member_id")
    private Long systemMemberId;

    /**
     * 消息阅读状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 首次阅读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

    /**
     * 归档时间
     */
    @TableField("archive_time")
    private LocalDateTime archiveTime;

    /**
     * 接收记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 接收记录更新时间
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getSystemMemberId() {
        return systemMemberId;
    }

    public void setSystemMemberId(Long systemMemberId) {
        this.systemMemberId = systemMemberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public LocalDateTime getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(LocalDateTime archiveTime) {
        this.archiveTime = archiveTime;
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
        return "MsgRecipient{" +
            "id = " + id +
            ", messageId = " + messageId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", accountId = " + accountId +
            ", systemMemberId = " + systemMemberId +
            ", status = " + status +
            ", readTime = " + readTime +
            ", archiveTime = " + archiveTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
