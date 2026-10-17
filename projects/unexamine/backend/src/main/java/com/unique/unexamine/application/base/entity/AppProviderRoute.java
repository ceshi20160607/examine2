package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台事件路由
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_provider_route")
public class AppProviderRoute {

    /**
     * 外部平台事件路由主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属外部平台连接主键
     */
    @TableField("connection_id")
    private Long connectionId;

    /**
     * 规范化平台事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 已发布应用授权资源类型
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 已发布应用授权资源业务编码
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 已发布应用授权动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 目标系统主键
     */
    @TableField("target_system_id")
    private Long targetSystemId;

    /**
     * 目标租户主键
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 路由状态：ACTIVE启用、DISABLED停用
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

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

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public Long getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(Long targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppProviderRoute{" +
            "id = " + id +
            ", connectionId = " + connectionId +
            ", eventCode = " + eventCode +
            ", resourceType = " + resourceType +
            ", resourceId = " + resourceId +
            ", actionCode = " + actionCode +
            ", targetSystemId = " + targetSystemId +
            ", targetTenantId = " + targetTenantId +
            ", status = " + status +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", version = " + version +
            "}";
    }
}
