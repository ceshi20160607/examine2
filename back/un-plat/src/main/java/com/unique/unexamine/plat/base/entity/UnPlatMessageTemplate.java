package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台消息模板
 * </p>
 *
 * @author Template Base
 * @since 2026-11-06
 */
@TableName("un_plat_message_template")
public class UnPlatMessageTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台消息模板主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台唯一模板编码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 消息标题模板
     */
    @TableField("title_template")
    private String titleTemplate;

    /**
     * 消息正文模板
     */
    @TableField("content_template")
    private String contentTemplate;

    /**
     * 允许填充的变量名数组
     */
    @TableField("variable_keys_json")
    private String variableKeysJson;

    /**
     * 发送渠道，目前为站内消息 IN_APP
     */
    @TableField("channel")
    private String channel;

    /**
     * 平台业务跳转类型，不允许直接跳系统详情
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 草稿、已发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 模板创建账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public void setTitleTemplate(String titleTemplate) {
        this.titleTemplate = titleTemplate;
    }

    public String getContentTemplate() {
        return contentTemplate;
    }

    public void setContentTemplate(String contentTemplate) {
        this.contentTemplate = contentTemplate;
    }

    public String getVariableKeysJson() {
        return variableKeysJson;
    }

    public void setVariableKeysJson(String variableKeysJson) {
        this.variableKeysJson = variableKeysJson;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
