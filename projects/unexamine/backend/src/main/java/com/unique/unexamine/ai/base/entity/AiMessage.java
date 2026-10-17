package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * AI 对话消息与结构化识别内容
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_message")
public class AiMessage {

    /**
     * 对话消息主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 AI 对话标识
     */
    @TableField("conversation_id")
    private Long conversationId;

    /**
     * 消息角色：用户、助手或工具
     */
    @TableField("role_type")
    private String roleType;

    /**
     * 消息正文
     */
    @TableField("content_text")
    private String contentText;

    /**
     * 结构化识别结果或呈现内容
     */
    @TableField("structured_content_json")
    private String structuredContentJson;

    /**
     * 模型调用用量与费用摘要
     */
    @TableField("model_usage_json")
    private String modelUsageJson;

    /**
     * 失败错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getStructuredContentJson() {
        return structuredContentJson;
    }

    public void setStructuredContentJson(String structuredContentJson) {
        this.structuredContentJson = structuredContentJson;
    }

    public String getModelUsageJson() {
        return modelUsageJson;
    }

    public void setModelUsageJson(String modelUsageJson) {
        this.modelUsageJson = modelUsageJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AiMessage{" +
            "id = " + id +
            ", conversationId = " + conversationId +
            ", roleType = " + roleType +
            ", contentText = " + contentText +
            ", structuredContentJson = " + structuredContentJson +
            ", modelUsageJson = " + modelUsageJson +
            ", errorCode = " + errorCode +
            ", createTime = " + createTime +
            "}";
    }
}
