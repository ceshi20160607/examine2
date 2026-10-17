package com.unique.unexamine.authentication.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统角色的字段读取写入策略
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_auth_field_policy")
public class AuthorizationFieldPolicy {

    /**
     * 字段访问策略主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 策略范围类型，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统标识，平台策略为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识，平台策略为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 业务查询使用的角色标识
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 平台策略对应的平台角色标识
     */
    @TableField("platform_role_id")
    private Long platformRoleId;

    /**
     * 系统策略对应的系统角色标识
     */
    @TableField("system_role_id")
    private Long systemRoleId;

    /**
     * 策略约束的资源编码
     */
    @TableField("resource_code")
    private String resourceCode;

    /**
     * 策略约束的字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 策略生效渠道
     */
    @TableField("`channel`")
    private String channel;

    /**
     * 是否允许读取字段
     */
    @TableField("readable")
    private Boolean readable;

    /**
     * 是否允许写入字段
     */
    @TableField("writable")
    private Boolean writable;

    /**
     * 字段脱敏规则
     */
    @TableField("mask_strategy")
    private String maskStrategy;

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

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getPlatformRoleId() {
        return platformRoleId;
    }

    public void setPlatformRoleId(Long platformRoleId) {
        this.platformRoleId = platformRoleId;
    }

    public Long getSystemRoleId() {
        return systemRoleId;
    }

    public void setSystemRoleId(Long systemRoleId) {
        this.systemRoleId = systemRoleId;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Boolean getReadable() {
        return readable;
    }

    public void setReadable(Boolean readable) {
        this.readable = readable;
    }

    public Boolean getWritable() {
        return writable;
    }

    public void setWritable(Boolean writable) {
        this.writable = writable;
    }

    public String getMaskStrategy() {
        return maskStrategy;
    }

    public void setMaskStrategy(String maskStrategy) {
        this.maskStrategy = maskStrategy;
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
        return "AuthorizationFieldPolicy{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", roleId = " + roleId +
            ", platformRoleId = " + platformRoleId +
            ", systemRoleId = " + systemRoleId +
            ", resourceCode = " + resourceCode +
            ", fieldCode = " + fieldCode +
            ", channel = " + channel +
            ", readable = " + readable +
            ", writable = " + writable +
            ", maskStrategy = " + maskStrategy +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
