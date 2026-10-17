package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台账号一次性密码找回请求
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_password_reset")
public class PlatPasswordReset {

    /**
     * 密码找回请求主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被找回密码的平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 一次性随机令牌的 SHA-256 摘要
     */
    @TableField("token_hash")
    private String tokenHash;

    /**
     * 请求创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 令牌失效时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 令牌消费或被新请求取代的时间
     */
    @TableField("used_time")
    private LocalDateTime usedTime;

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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getUsedTime() {
        return usedTime;
    }

    public void setUsedTime(LocalDateTime usedTime) {
        this.usedTime = usedTime;
    }

    @Override
    public String toString() {
        return "PlatPasswordReset{" +
            "id = " + id +
            ", accountId = " + accountId +
            ", tokenHash = " + tokenHash +
            ", createTime = " + createTime +
            ", expireTime = " + expireTime +
            ", usedTime = " + usedTime +
            "}";
    }
}
