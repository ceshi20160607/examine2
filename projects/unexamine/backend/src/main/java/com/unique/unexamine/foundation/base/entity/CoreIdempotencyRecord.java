package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务操作幂等请求及首次执行结果
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_idempotency_record")
public class CoreIdempotencyRecord {

    /**
     * 幂等请求记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 幂等键所属业务范围标识
     */
    @TableField("context_key")
    private String contextKey;

    /**
     * 被保护的业务操作编码
     */
    @TableField("operation_code")
    private String operationCode;

    /**
     * 调用方提供的幂等请求标识
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 原始请求内容摘要，用于识别同键不同请求
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 幂等请求处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 首次执行的业务结果编码
     */
    @TableField("response_code")
    private String responseCode;

    /**
     * 可供重复请求返回的首次执行结果
     */
    @TableField("response_json")
    private String responseJson;

    /**
     * 在途操作独占锁截止时间
     */
    @TableField("lock_expire_time")
    private LocalDateTime lockExpireTime;

    /**
     * 幂等记录有效截止时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 幂等记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 幂等记录最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 幂等记录乐观锁版本
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

    public String getContextKey() {
        return contextKey;
    }

    public void setContextKey(String contextKey) {
        this.contextKey = contextKey;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public LocalDateTime getLockExpireTime() {
        return lockExpireTime;
    }

    public void setLockExpireTime(LocalDateTime lockExpireTime) {
        this.lockExpireTime = lockExpireTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
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
        return "CoreIdempotencyRecord{" +
            "id = " + id +
            ", contextKey = " + contextKey +
            ", operationCode = " + operationCode +
            ", idempotencyKey = " + idempotencyKey +
            ", requestHash = " + requestHash +
            ", status = " + status +
            ", responseCode = " + responseCode +
            ", responseJson = " + responseJson +
            ", lockExpireTime = " + lockExpireTime +
            ", expireTime = " + expireTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
