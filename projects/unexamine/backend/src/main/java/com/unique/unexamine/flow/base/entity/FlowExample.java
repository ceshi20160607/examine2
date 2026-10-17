package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录的一轮审批运行实例
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_example")
public class FlowExample {

    /**
     * 审批运行实例主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 发起时采用的发布版本主键
     */
    @TableField("flow_version_id")
    private Long flowVersionId;

    /**
     * 实例所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 实例所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 实例绑定的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 实例绑定的业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 同一记录的发起轮次
     */
    @TableField("attempt_number")
    private Integer attemptNumber;

    /**
     * 发起人的系统成员主键
     */
    @TableField("applicant_member_id")
    private Long applicantMemberId;

    /**
     * 实例创建时复制的流程版本快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 实例状态，待审批、通过或拒绝
     */
    @TableField("`status`")
    private String status;

    /**
     * 实例发起时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 实例结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 实例乐观锁版本
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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getFlowVersionId() {
        return flowVersionId;
    }

    public void setFlowVersionId(Long flowVersionId) {
        this.flowVersionId = flowVersionId;
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

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Long getApplicantMemberId() {
        return applicantMemberId;
    }

    public void setApplicantMemberId(Long applicantMemberId) {
        this.applicantMemberId = applicantMemberId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
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

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FlowExample{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", flowVersionId = " + flowVersionId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", attemptNumber = " + attemptNumber +
            ", applicantMemberId = " + applicantMemberId +
            ", snapshotJson = " + snapshotJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
