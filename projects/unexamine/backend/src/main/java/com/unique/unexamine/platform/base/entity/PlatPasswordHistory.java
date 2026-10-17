package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 账号历史口令
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_password_history")
public class PlatPasswordHistory {

    /**
     * 账号历史口令主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 历史口令哈希值
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 对应口令凭据版本号
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 该口令启用时间
     */
    @TableField("change_time")
    private LocalDateTime changeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    @Override
    public String toString() {
        return "PlatPasswordHistory{" +
            "id = " + id +
            ", accountId = " + accountId +
            ", passwordHash = " + passwordHash +
            ", credentialVersion = " + credentialVersion +
            ", changeTime = " + changeTime +
            "}";
    }
}
