package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 保存发起快照的审批实例
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_example")
public class UnExamineExample implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 来源审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 发起时审批定义发布版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 审批业务所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 审批业务所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 被审批业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 被审批业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 外部发起所用受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 内部实际发起平台账号主键
     */
    @TableField("initiator_account_id")
    private Long initiatorAccountId;

    /**
     * 外部发起人标识
     */
    @TableField("initiator_external_id")
    private String initiatorExternalId;

    /**
     * 审批中通过拒绝撤回终止或异常状态
     */
    @TableField("status")
    private String status;

    /**
     * 发起时完整审批字段人员规则和业务值快照
     */
    @TableField("runtime_snapshot_json")
    private String runtimeSnapshotJson;

    /**
     * 审批发起时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 审批完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 实例更新时间
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

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getInitiatorAccountId() {
        return initiatorAccountId;
    }

    public void setInitiatorAccountId(Long initiatorAccountId) {
        this.initiatorAccountId = initiatorAccountId;
    }

    public String getInitiatorExternalId() {
        return initiatorExternalId;
    }

    public void setInitiatorExternalId(String initiatorExternalId) {
        this.initiatorExternalId = initiatorExternalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuntimeSnapshotJson() {
        return runtimeSnapshotJson;
    }

    public void setRuntimeSnapshotJson(String runtimeSnapshotJson) {
        this.runtimeSnapshotJson = runtimeSnapshotJson;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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
