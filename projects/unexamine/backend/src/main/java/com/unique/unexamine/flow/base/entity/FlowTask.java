package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 节点执行轮次的待办任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_task")
public class FlowTask {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例ID
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 任务所属的节点执行轮次
     */
    @TableField("execution_id")
    private Long executionId;

    /**
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 待办任务类型
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前处理人平台账号ID
     */
    @TableField("assignee_account_id")
    private Long assigneeAccountId;

    /**
     * 当前处理人在实际运行租户的成员ID
     */
    @TableField("assignee_tenant_member_id")
    private Long assigneeTenantMemberId;

    /**
     * 外部审批人所属应用ID；内部任务为空
     */
    @TableField("assignee_application_id")
    private Long assigneeApplicationId;

    /**
     * 应用命名空间内的外部审批人标识；候选池任务为空
     */
    @TableField("assignee_external_principal_id")
    private String assigneeExternalPrincipalId;

    /**
     * 任务创建时处理人解析快照
     */
    @TableField("assignee_snapshot_json")
    private String assigneeSnapshotJson;

    /**
     * 任务应完成时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 任务领取时间
     */
    @TableField("claim_time")
    private LocalDateTime claimTime;

    /**
     * 任务完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最后修改时间
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

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public void setAssigneeAccountId(Long assigneeAccountId) {
        this.assigneeAccountId = assigneeAccountId;
    }

    public Long getAssigneeTenantMemberId() {
        return assigneeTenantMemberId;
    }

    public void setAssigneeTenantMemberId(Long assigneeTenantMemberId) {
        this.assigneeTenantMemberId = assigneeTenantMemberId;
    }

    public Long getAssigneeApplicationId() {
        return assigneeApplicationId;
    }

    public void setAssigneeApplicationId(Long assigneeApplicationId) {
        this.assigneeApplicationId = assigneeApplicationId;
    }

    public String getAssigneeExternalPrincipalId() {
        return assigneeExternalPrincipalId;
    }

    public void setAssigneeExternalPrincipalId(String assigneeExternalPrincipalId) {
        this.assigneeExternalPrincipalId = assigneeExternalPrincipalId;
    }

    public String getAssigneeSnapshotJson() {
        return assigneeSnapshotJson;
    }

    public void setAssigneeSnapshotJson(String assigneeSnapshotJson) {
        this.assigneeSnapshotJson = assigneeSnapshotJson;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public LocalDateTime getClaimTime() {
        return claimTime;
    }

    public void setClaimTime(LocalDateTime claimTime) {
        this.claimTime = claimTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
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
        return "FlowTask{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", executionId = " + executionId +
            ", nodeKey = " + nodeKey +
            ", taskType = " + taskType +
            ", status = " + status +
            ", assigneeAccountId = " + assigneeAccountId +
            ", assigneeTenantMemberId = " + assigneeTenantMemberId +
            ", assigneeApplicationId = " + assigneeApplicationId +
            ", assigneeExternalPrincipalId = " + assigneeExternalPrincipalId +
            ", assigneeSnapshotJson = " + assigneeSnapshotJson +
            ", dueTime = " + dueTime +
            ", claimTime = " + claimTime +
            ", completeTime = " + completeTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
