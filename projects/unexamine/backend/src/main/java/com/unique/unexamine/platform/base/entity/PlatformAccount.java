package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 统一平台账号
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_account")
public class PlatformAccount {

    /**
     * 平台账号标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 全平台唯一登录名
     */
    @TableField("username")
    private String username;

    /**
     * 带盐的单向口令哈希
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 电子邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 手机号码
     */
    @TableField("mobile")
    private String mobile;

    /**
     * 账号状态：1启用，0停用
     */
    @TableField("`status`")
    private Boolean status;

    /**
     * 最近成功登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 账号创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 账号修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
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

    @Override
    public String toString() {
        return "PlatformAccount{" +
            "id = " + id +
            ", username = " + username +
            ", passwordHash = " + passwordHash +
            ", displayName = " + displayName +
            ", email = " + email +
            ", mobile = " + mobile +
            ", status = " + status +
            ", lastLoginTime = " + lastLoginTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
