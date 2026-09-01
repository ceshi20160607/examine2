package com.unique.unexamine.flow.base.entity;

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
@TableName("flow_task")
public class FlowTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("task_type")
    private String taskType;

    @TableField("`status`")
    private String status;

    @TableField("assignee_account_id")
    private Long assigneeAccountId;

    @TableField("assignee_snapshot_json")
    private String assigneeSnapshotJson;

    @TableField("due_at")
    private LocalDateTime dueAt;

    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

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

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
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

    public String getAssigneeSnapshotJson() {
        return assigneeSnapshotJson;
    }

    public void setAssigneeSnapshotJson(String assigneeSnapshotJson) {
        this.assigneeSnapshotJson = assigneeSnapshotJson;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
        return "FlowTask{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", nodeKey = " + nodeKey +
            ", taskType = " + taskType +
            ", status = " + status +
            ", assigneeAccountId = " + assigneeAccountId +
            ", assigneeSnapshotJson = " + assigneeSnapshotJson +
            ", dueAt = " + dueAt +
            ", claimedAt = " + claimedAt +
            ", completedAt = " + completedAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
