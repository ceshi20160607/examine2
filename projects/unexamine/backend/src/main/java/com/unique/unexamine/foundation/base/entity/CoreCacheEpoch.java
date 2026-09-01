package com.unique.unexamine.foundation.base.entity;

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
@TableName("core_cache_epoch")
public class CoreCacheEpoch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_key")
    private String contextKey;

    @TableField("cache_namespace")
    private String cacheNamespace;

    @TableField("epoch_value")
    private Long epochValue;

    @TableField("reason")
    private String reason;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public String getCacheNamespace() {
        return cacheNamespace;
    }

    public void setCacheNamespace(String cacheNamespace) {
        this.cacheNamespace = cacheNamespace;
    }

    public Long getEpochValue() {
        return epochValue;
    }

    public void setEpochValue(Long epochValue) {
        this.epochValue = epochValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "CoreCacheEpoch{" +
            "id = " + id +
            ", contextKey = " + contextKey +
            ", cacheNamespace = " + cacheNamespace +
            ", epochValue = " + epochValue +
            ", reason = " + reason +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
