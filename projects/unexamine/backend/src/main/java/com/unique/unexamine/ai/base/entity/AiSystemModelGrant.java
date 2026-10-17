package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统模型授权草稿与状态
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_system_model_grant")
public class AiSystemModelGrant {

    /**
     * 模型授权主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 授权所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 平台模型标识
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 获授权系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统可用额度与并发限制
     */
    @TableField("usage_limit_json")
    private String usageLimitJson;

    /**
     * 授权状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 授权平台账号标识
     */
    @TableField("granted_by_account_id")
    private Long grantedByAccountId;

    /**
     * 授权时间
     */
    @TableField("grant_time")
    private LocalDateTime grantTime;

    /**
     * 撤销时间
     */
    @TableField("revoke_time")
    private LocalDateTime revokeTime;

    /**
     * 授权草稿乐观锁版本
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

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getUsageLimitJson() {
        return usageLimitJson;
    }

    public void setUsageLimitJson(String usageLimitJson) {
        this.usageLimitJson = usageLimitJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getGrantedByAccountId() {
        return grantedByAccountId;
    }

    public void setGrantedByAccountId(Long grantedByAccountId) {
        this.grantedByAccountId = grantedByAccountId;
    }

    public LocalDateTime getGrantTime() {
        return grantTime;
    }

    public void setGrantTime(LocalDateTime grantTime) {
        this.grantTime = grantTime;
    }

    public LocalDateTime getRevokeTime() {
        return revokeTime;
    }

    public void setRevokeTime(LocalDateTime revokeTime) {
        this.revokeTime = revokeTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AiSystemModelGrant{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", modelId = " + modelId +
            ", systemId = " + systemId +
            ", usageLimitJson = " + usageLimitJson +
            ", status = " + status +
            ", grantedByAccountId = " + grantedByAccountId +
            ", grantTime = " + grantTime +
            ", revokeTime = " + revokeTime +
            ", version = " + version +
            "}";
    }
}
