package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ai_message")
public class AiMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("role_type")
    private String roleType;

    @TableField("content_text")
    private String contentText;

    @TableField("structured_content_json")
    private String structuredContentJson;

    @TableField("model_usage_json")
    private String modelUsageJson;

    @TableField("error_code")
    private String errorCode;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
            ", createdAt = " + createdAt +
            "}";
    }
}
