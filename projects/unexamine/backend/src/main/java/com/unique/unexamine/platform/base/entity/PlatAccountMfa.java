package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 账号多因素认证方式
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_account_mfa")
public class PlatAccountMfa {

    /**
     * 账号多因素认证方式主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 多因素认证方式类型
     */
    @TableField("method_type")
    private String methodType;

    /**
     * 认证密钥的受控引用
     */
    @TableField("secret_ref")
    private String secretRef;

    /**
     * 认证方式展示标签
     */
    @TableField("display_label")
    private String displayLabel;

    /**
     * 认证方式最近验证时间
     */
    @TableField("verify_time")
    private LocalDateTime verifyTime;

    /**
     * 账号多因素认证方式状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 账号多因素认证方式创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 账号多因素认证方式更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 账号多因素认证方式乐观锁版本
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getMethodType() {
        return methodType;
    }

    public void setMethodType(String methodType) {
        this.methodType = methodType;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public LocalDateTime getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(LocalDateTime verifyTime) {
        this.verifyTime = verifyTime;
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
        return "PlatAccountMfa{" +
            "id = " + id +
            ", accountId = " + accountId +
            ", methodType = " + methodType +
            ", secretRef = " + secretRef +
            ", displayLabel = " + displayLabel +
            ", verifyTime = " + verifyTime +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
