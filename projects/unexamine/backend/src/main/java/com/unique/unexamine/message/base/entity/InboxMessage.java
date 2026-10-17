package com.unique.unexamine.message.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 审批旅程的站内消息收件箱
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_message")
public class InboxMessage {

    /**
     * 站内消息主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 消息所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 收件平台账号主键
     */
    @TableField("recipient_account_id")
    private Long recipientAccountId;

    /**
     * 审批待办或审批结果等消息类别
     */
    @TableField("kind")
    private String kind;

    /**
     * 不包含业务敏感字段的消息标题
     */
    @TableField("title")
    private String title;

    /**
     * 目标业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 关联业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 关联审批任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 收件人首次标记已读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

    /**
     * 消息生成时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getRecipientAccountId() {
        return recipientAccountId;
    }

    public void setRecipientAccountId(Long recipientAccountId) {
        this.recipientAccountId = recipientAccountId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "InboxMessage{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", recipientAccountId = " + recipientAccountId +
            ", kind = " + kind +
            ", title = " + title +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", taskId = " + taskId +
            ", readTime = " + readTime +
            ", createTime = " + createTime +
            "}";
    }
}
