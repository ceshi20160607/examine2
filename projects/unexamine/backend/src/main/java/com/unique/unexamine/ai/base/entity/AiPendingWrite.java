package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 人工确认前不得写入正式业务表的 AI 提议
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_pending_write")
public class AiPendingWrite {

    /**
     * 待确认 AI 写入主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提出建议的 AI 执行标识
     */
    @TableField("execution_id")
    private Long executionId;

    /**
     * 提出建议的执行步骤标识
     */
    @TableField("step_id")
    private Long stepId;

    /**
     * 待确认写入平台或系统范围
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 待确认写入所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统写入的目标系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统写入的目标租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 待写入目标类别；平台只允许任务、消息、日志
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 已有目标业务标识；新增时为空
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 系统业务记录或配置目标模块标识
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 确认时用于防止覆盖后续修改的目标版本
     */
    @TableField("target_expected_version")
    private Integer targetExpectedVersion;

    /**
     * 供用户确认的结构化提议，正式数据尚未写入
     */
    @TableField("proposed_payload_json")
    private String proposedPayloadJson;

    /**
     * 字段映射、置信度和影响范围预览
     */
    @TableField("preview_json")
    private String previewJson;

    /**
     * 等待确认、拒绝或执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 人工确认平台账号标识
     */
    @TableField("confirmed_by_account_id")
    private Long confirmedByAccountId;

    /**
     * 平台确认者成员账号，系统确认时为空
     */
    @TableField("confirmed_by_platform_member_account_id")
    private Long confirmedByPlatformMemberAccountId;

    /**
     * 系统写入确认成员标识
     */
    @TableField("confirmed_by_member_id")
    private Long confirmedByMemberId;

    /**
     * 人工确认时间
     */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /**
     * 确认后业务写入结果及审计关联
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 拒绝或失败原因摘要
     */
    @TableField("error_message")
    private String errorMessage;

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
     * 确认状态乐观锁版本
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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public Integer getTargetExpectedVersion() {
        return targetExpectedVersion;
    }

    public void setTargetExpectedVersion(Integer targetExpectedVersion) {
        this.targetExpectedVersion = targetExpectedVersion;
    }

    public String getProposedPayloadJson() {
        return proposedPayloadJson;
    }

    public void setProposedPayloadJson(String proposedPayloadJson) {
        this.proposedPayloadJson = proposedPayloadJson;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public void setPreviewJson(String previewJson) {
        this.previewJson = previewJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfirmedByAccountId() {
        return confirmedByAccountId;
    }

    public void setConfirmedByAccountId(Long confirmedByAccountId) {
        this.confirmedByAccountId = confirmedByAccountId;
    }

    public Long getConfirmedByPlatformMemberAccountId() {
        return confirmedByPlatformMemberAccountId;
    }

    public void setConfirmedByPlatformMemberAccountId(Long confirmedByPlatformMemberAccountId) {
        this.confirmedByPlatformMemberAccountId = confirmedByPlatformMemberAccountId;
    }

    public Long getConfirmedByMemberId() {
        return confirmedByMemberId;
    }

    public void setConfirmedByMemberId(Long confirmedByMemberId) {
        this.confirmedByMemberId = confirmedByMemberId;
    }

    public LocalDateTime getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(LocalDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
        return "AiPendingWrite{" +
            "id = " + id +
            ", executionId = " + executionId +
            ", stepId = " + stepId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", targetType = " + targetType +
            ", targetId = " + targetId +
            ", targetModuleId = " + targetModuleId +
            ", targetExpectedVersion = " + targetExpectedVersion +
            ", proposedPayloadJson = " + proposedPayloadJson +
            ", previewJson = " + previewJson +
            ", status = " + status +
            ", confirmedByAccountId = " + confirmedByAccountId +
            ", confirmedByPlatformMemberAccountId = " + confirmedByPlatformMemberAccountId +
            ", confirmedByMemberId = " + confirmedByMemberId +
            ", confirmTime = " + confirmTime +
            ", resultJson = " + resultJson +
            ", errorMessage = " + errorMessage +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
