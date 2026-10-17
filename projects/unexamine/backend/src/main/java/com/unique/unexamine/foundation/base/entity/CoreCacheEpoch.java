package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 按上下文隔离的缓存命名空间失效版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_cache_epoch")
public class CoreCacheEpoch {

    /**
     * 缓存失效世代主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 缓存归属的业务上下文标识
     */
    @TableField("context_key")
    private String contextKey;

    /**
     * 需要失效的缓存命名空间
     */
    @TableField("cache_namespace")
    private String cacheNamespace;

    /**
     * 当前缓存失效世代号
     */
    @TableField("epoch_value")
    private Long epochValue;

    /**
     * 推进缓存世代的业务原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 缓存世代最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 缓存世代乐观锁版本
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
        return "CoreCacheEpoch{" +
            "id = " + id +
            ", contextKey = " + contextKey +
            ", cacheNamespace = " + cacheNamespace +
            ", epochValue = " + epochValue +
            ", reason = " + reason +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
