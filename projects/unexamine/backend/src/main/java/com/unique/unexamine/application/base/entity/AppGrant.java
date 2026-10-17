package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用按目标资源和动作配置的访问授权草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_grant")
public class AppGrant {

    /**
     * 授权主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 授权目标：PLATFORM平台、SYSTEM系统
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标系统主键，平台授权为空
     */
    @TableField("target_system_id")
    private Long targetSystemId;

    /**
     * 目标租户主键，平台授权为空
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 授权资源类型：FLOW流程、MODULE模块
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 目标资源业务编码
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 允许调用的动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 授权数据范围规则
     */
    @TableField("data_scope_json")
    private String dataScopeJson;

    /**
     * 限流和访问通道控制规则
     */
    @TableField("rate_limit_json")
    private String rateLimitJson;

    /**
     * 授权启停状态
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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
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

    public String getDataScopeJson() {
        return dataScopeJson;
    }

    public void setDataScopeJson(String dataScopeJson) {
        this.dataScopeJson = dataScopeJson;
    }

    public String getRateLimitJson() {
        return rateLimitJson;
    }

    public void setRateLimitJson(String rateLimitJson) {
        this.rateLimitJson = rateLimitJson;
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
        return "AppGrant{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", targetType = " + targetType +
            ", targetSystemId = " + targetSystemId +
            ", targetTenantId = " + targetTenantId +
            ", resourceType = " + resourceType +
            ", resourceId = " + resourceId +
            ", actionCode = " + actionCode +
            ", dataScopeJson = " + dataScopeJson +
            ", rateLimitJson = " + rateLimitJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
