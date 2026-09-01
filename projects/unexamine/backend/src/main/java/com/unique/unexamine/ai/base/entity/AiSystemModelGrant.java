package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ai_system_model_grant")
public class AiSystemModelGrant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("model_id")
    private Long modelId;

    @TableField("system_id")
    private Long systemId;

    @TableField("usage_limit_json")
    private String usageLimitJson;

    @TableField("`status`")
    private String status;

    @TableField("granted_by_account_id")
    private Long grantedByAccountId;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
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
            ", modelId = " + modelId +
            ", systemId = " + systemId +
            ", usageLimitJson = " + usageLimitJson +
            ", status = " + status +
            ", grantedByAccountId = " + grantedByAccountId +
            ", grantedAt = " + grantedAt +
            ", revokedAt = " + revokedAt +
            ", version = " + version +
            "}";
    }
}
