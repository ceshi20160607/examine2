package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台、系统和租户范围内的普通业务配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_setting")
public class CoreSetting {

    /**
     * 配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 配置归属：平台、系统或租户
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统主键，平台配置为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键，平台和系统配置为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 业务配置类别
     */
    @TableField("category")
    private String category;

    /**
     * 归属范围内的配置键
     */
    @TableField("setting_key")
    private String settingKey;

    /**
     * 配置值类型
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 业务配置值，不承载固定实体关系
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 是否属于敏感配置
     */
    @TableField("`sensitive`")
    private Boolean sensitive;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
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
        return "CoreSetting{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", category = " + category +
            ", settingKey = " + settingKey +
            ", valueType = " + valueType +
            ", valueJson = " + valueJson +
            ", sensitive = " + sensitive +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
