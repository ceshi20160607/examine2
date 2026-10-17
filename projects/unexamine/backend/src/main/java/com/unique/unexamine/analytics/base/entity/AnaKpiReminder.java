package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * KPI 结果关联同上下文待办与消息的提醒投影
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_kpi_reminder")
public class AnaKpiReminder {

    /**
     * KPI 未达标提醒主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 触发提醒的 KPI 计算结果主键
     */
    @TableField("kpi_result_id")
    private Long kpiResultId;

    /**
     * 提醒投影上下文，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 平台或系统所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统提醒所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统提醒实际投递租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 平台待办投影主键
     */
    @TableField("platform_todo_id")
    private Long platformTodoId;

    /**
     * 系统待办投影主键
     */
    @TableField("system_todo_id")
    private Long systemTodoId;

    /**
     * 平台消息投影主键
     */
    @TableField("platform_message_id")
    private Long platformMessageId;

    /**
     * 系统消息投影主键
     */
    @TableField("system_message_id")
    private Long systemMessageId;

    /**
     * 提醒接收者账号主键
     */
    @TableField("recipient_account_id")
    private Long recipientAccountId;

    /**
     * 平台提醒接收者账号，系统提醒为空
     */
    @TableField("recipient_platform_account_id")
    private Long recipientPlatformAccountId;

    /**
     * 系统提醒接收者在当前系统的成员主键
     */
    @TableField("recipient_system_member_id")
    private Long recipientSystemMemberId;

    /**
     * 待发送、已发送、已确认或失败状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 发送时间
     */
    @TableField("send_time")
    private LocalDateTime sendTime;

    /**
     * 确认时间
     */
    @TableField("acknowledge_time")
    private LocalDateTime acknowledgeTime;

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
     * 乐观锁版本号
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

    public Long getKpiResultId() {
        return kpiResultId;
    }

    public void setKpiResultId(Long kpiResultId) {
        this.kpiResultId = kpiResultId;
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

    public Long getPlatformTodoId() {
        return platformTodoId;
    }

    public void setPlatformTodoId(Long platformTodoId) {
        this.platformTodoId = platformTodoId;
    }

    public Long getSystemTodoId() {
        return systemTodoId;
    }

    public void setSystemTodoId(Long systemTodoId) {
        this.systemTodoId = systemTodoId;
    }

    public Long getPlatformMessageId() {
        return platformMessageId;
    }

    public void setPlatformMessageId(Long platformMessageId) {
        this.platformMessageId = platformMessageId;
    }

    public Long getSystemMessageId() {
        return systemMessageId;
    }

    public void setSystemMessageId(Long systemMessageId) {
        this.systemMessageId = systemMessageId;
    }

    public Long getRecipientAccountId() {
        return recipientAccountId;
    }

    public void setRecipientAccountId(Long recipientAccountId) {
        this.recipientAccountId = recipientAccountId;
    }

    public Long getRecipientPlatformAccountId() {
        return recipientPlatformAccountId;
    }

    public void setRecipientPlatformAccountId(Long recipientPlatformAccountId) {
        this.recipientPlatformAccountId = recipientPlatformAccountId;
    }

    public Long getRecipientSystemMemberId() {
        return recipientSystemMemberId;
    }

    public void setRecipientSystemMemberId(Long recipientSystemMemberId) {
        this.recipientSystemMemberId = recipientSystemMemberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSendTime() {
        return sendTime;
    }

    public void setSendTime(LocalDateTime sendTime) {
        this.sendTime = sendTime;
    }

    public LocalDateTime getAcknowledgeTime() {
        return acknowledgeTime;
    }

    public void setAcknowledgeTime(LocalDateTime acknowledgeTime) {
        this.acknowledgeTime = acknowledgeTime;
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
        return "AnaKpiReminder{" +
            "id = " + id +
            ", kpiResultId = " + kpiResultId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", platformTodoId = " + platformTodoId +
            ", systemTodoId = " + systemTodoId +
            ", platformMessageId = " + platformMessageId +
            ", systemMessageId = " + systemMessageId +
            ", recipientAccountId = " + recipientAccountId +
            ", recipientPlatformAccountId = " + recipientPlatformAccountId +
            ", recipientSystemMemberId = " + recipientSystemMemberId +
            ", status = " + status +
            ", sendTime = " + sendTime +
            ", acknowledgeTime = " + acknowledgeTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
