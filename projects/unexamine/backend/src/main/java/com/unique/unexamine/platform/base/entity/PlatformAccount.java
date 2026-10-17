package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台统一账号
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
     * 平台唯一登录用户名
     */
    @TableField("username")
    private String username;

    /**
     * 带算法与盐信息的口令哈希编码
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 账号展示名称
     */
    @TableField("display_name")
    private String displayName;

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
     * 账号启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 账号创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 账号更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 账号乐观锁版本
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
            ", passwordHash = " + passwordHash +
            ", displayName = " + displayName +
            ", email = " + email +
            ", mobile = " + mobile +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
