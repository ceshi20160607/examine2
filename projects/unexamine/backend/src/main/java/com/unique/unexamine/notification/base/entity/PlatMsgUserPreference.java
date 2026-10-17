package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台成员消息渠道和免打扰偏好
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_msg_user_preference")
public class PlatMsgUserPreference {

    /**
     * 平台消息偏好主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 偏好所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 偏好所属平台成员账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 通知事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 偏好对应投递渠道
     */
    @TableField("`channel`")
    private String channel;

    /**
     * 是否启用此类通知
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 免打扰时段定义
     */
    @TableField("quiet_hours_json")
    private String quietHoursJson;

    /**
     * 偏好创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 偏好更新时间
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

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getQuietHoursJson() {
        return quietHoursJson;
    }

    public void setQuietHoursJson(String quietHoursJson) {
        this.quietHoursJson = quietHoursJson;
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
        return "PlatMsgUserPreference{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", accountId = " + accountId +
            ", eventCode = " + eventCode +
            ", channel = " + channel +
            ", enabled = " + enabled +
            ", quietHoursJson = " + quietHoursJson +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
