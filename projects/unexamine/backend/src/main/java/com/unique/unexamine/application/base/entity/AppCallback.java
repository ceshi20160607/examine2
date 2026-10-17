package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用事件回调配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_callback")
public class AppCallback {

    /**
     * 回调主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 回调业务类型
     */
    @TableField("callback_type")
    private String callbackType;

    /**
     * 接收回调的HTTPS地址
     */
    @TableField("url")
    private String url;

    /**
     * 订阅的业务事件编码列表
     */
    @TableField("event_codes_json")
    private String eventCodesJson;

    /**
     * 回调签名密钥安全引用
     */
    @TableField("signing_secret_ref")
    private String signingSecretRef;

    /**
     * 单次回调超时毫秒数
     */
    @TableField("timeout_millis")
    private Integer timeoutMillis;

    /**
     * 最大投递次数
     */
    @TableField("max_attempts")
    private Integer maxAttempts;

    /**
     * 回调启停状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getCallbackType() {
        return callbackType;
    }

    public void setCallbackType(String callbackType) {
        this.callbackType = callbackType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEventCodesJson() {
        return eventCodesJson;
    }

    public void setEventCodesJson(String eventCodesJson) {
        this.eventCodesJson = eventCodesJson;
    }

    public String getSigningSecretRef() {
        return signingSecretRef;
    }

    public void setSigningSecretRef(String signingSecretRef) {
        this.signingSecretRef = signingSecretRef;
    }

    public Integer getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
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
        return "AppCallback{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", callbackType = " + callbackType +
            ", url = " + url +
            ", eventCodesJson = " + eventCodesJson +
            ", signingSecretRef = " + signingSecretRef +
            ", timeoutMillis = " + timeoutMillis +
            ", maxAttempts = " + maxAttempts +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
