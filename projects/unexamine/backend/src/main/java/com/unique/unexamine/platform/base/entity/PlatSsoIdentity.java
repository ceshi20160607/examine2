package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 外部身份与平台账号绑定
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_sso_identity")
public class PlatSsoIdentity {

    /**
     * 外部身份与平台账号绑定主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台身份源标识
     */
    @TableField("provider_id")
    private Long providerId;

    /**
     * 外部身份主体标识
     */
    @TableField("external_subject")
    private String externalSubject;

    /**
     * 绑定的平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 外部身份属性快照
     */
    @TableField("attributes_json")
    private String attributesJson;

    /**
     * 该外部身份最近登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 外部身份与平台账号绑定创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 外部身份与平台账号绑定更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public void setExternalSubject(String externalSubject) {
        this.externalSubject = externalSubject;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
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
        return "PlatSsoIdentity{" +
            "id = " + id +
            ", providerId = " + providerId +
            ", externalSubject = " + externalSubject +
            ", accountId = " + accountId +
            ", attributesJson = " + attributesJson +
            ", lastLoginTime = " + lastLoginTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
