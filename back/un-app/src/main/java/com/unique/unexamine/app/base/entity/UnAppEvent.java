package com.unique.unexamine.app.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 受控应用事件配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_app_event")
public class UnAppEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用事件配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 稳定业务事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 事件所属模块或流程资源类型
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 被订阅的具体资源主键
     */
    @TableField("resource_id")
    private Long resourceId;

    /**
     * 该事件覆盖应用默认回调的地址
     */
    @TableField("callback_url")
    private String callbackUrl;

    /**
     * 签名重试过滤和推送配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布事件执行配置快照
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

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

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
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
}
