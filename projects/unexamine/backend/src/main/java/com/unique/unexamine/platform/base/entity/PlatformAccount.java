package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台账号
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_account")
public class PlatformAccount {

    /**
     * 平台账号主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名
     */
    @TableField("username")
    private String username;

    /**
     * 账号绑定邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 账号绑定手机号
     */
    @TableField("mobile")
    private String mobile;

    /**
     * 账号展示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 头像文件标识
     */
    @TableField("avatar_file_id")
    private Long avatarFileId;

    /**
     * 界面语言区域
     */
    @TableField("locale")
    private String locale;

    /**
     * 账号时区
     */
    @TableField("timezone")
    private String timezone;

    /**
     * 平台账号状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 最近登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 平台账号创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台账号更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台账号乐观锁版本
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
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
        return "PlatformAccount{" +
            "id = " + id +
            ", username = " + username +
            ", email = " + email +
            ", mobile = " + mobile +
            ", displayName = " + displayName +
            ", avatarFileId = " + avatarFileId +
            ", locale = " + locale +
            ", timezone = " + timezone +
            ", status = " + status +
            ", lastLoginTime = " + lastLoginTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
