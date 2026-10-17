package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台消息成员收件箱及回执
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_msg_recipient")
public class PlatMsgRecipient {

    /**
     * 平台消息接收记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台消息主键
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 接收平台成员账号主键
     */
    @TableField("account_id")
    private Long accountId;

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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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
        return "PlatMsgRecipient{" +
            "id = " + id +
            ", messageId = " + messageId +
            ", platformId = " + platformId +
            ", accountId = " + accountId +
            ", status = " + status +
            ", readTime = " + readTime +
            ", archiveTime = " + archiveTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
