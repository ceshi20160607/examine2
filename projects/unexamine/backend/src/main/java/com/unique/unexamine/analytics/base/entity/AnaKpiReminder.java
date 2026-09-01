package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ana_kpi_reminder")
public class AnaKpiReminder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("kpi_result_id")
    private Long kpiResultId;

    @TableField("todo_id")
    private Long todoId;

    @TableField("message_id")
    private Long messageId;

    @TableField("recipient_account_id")
    private Long recipientAccountId;

    @TableField("`status`")
    private String status;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKpiResultId() {
        return kpiResultId;
    }

    public void setKpiResultId(Long kpiResultId) {
        this.kpiResultId = kpiResultId;
    }

    public Long getTodoId() {
        return todoId;
    }

    public void setTodoId(Long todoId) {
        this.todoId = todoId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getRecipientAccountId() {
        return recipientAccountId;
    }

    public void setRecipientAccountId(Long recipientAccountId) {
        this.recipientAccountId = recipientAccountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AnaKpiReminder{" +
            "id = " + id +
            ", kpiResultId = " + kpiResultId +
            ", todoId = " + todoId +
            ", messageId = " + messageId +
            ", recipientAccountId = " + recipientAccountId +
            ", status = " + status +
            ", sentAt = " + sentAt +
            ", acknowledgedAt = " + acknowledgedAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
