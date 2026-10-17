package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统租户的受控特性开关
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_feature_flag")
public class CoreFeatureFlag {

    /**
     * 特性开关主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 特性开关归属：平台或系统租户
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 平台范围主键，系统租户开关为空
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统范围主键，平台开关为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统租户主键，平台开关为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 归属范围内的特性编码
     */
    @TableField("flag_key")
    private String flagKey;

    /**
     * 开关默认启用状态
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 生效时间、目标对象与安全回退规则
     */
    @TableField("rollout_json")
    private String rolloutJson;

    /**
     * 配置启停状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建时间，后续统一更名为 create_time
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间，后续统一更名为 update_time
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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRolloutJson() {
        return rolloutJson;
    }

    public void setRolloutJson(String rolloutJson) {
        this.rolloutJson = rolloutJson;
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
        return "CoreFeatureFlag{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", flagKey = " + flagKey +
            ", enabled = " + enabled +
            ", rolloutJson = " + rolloutJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
