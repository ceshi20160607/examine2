package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程定时与异步等待任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_job")
public class FlowJob {

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
     * 关联节点执行轮次ID
     */
    @TableField("execution_id")
    private Long executionId;

    /**
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 流程任务类型
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 流程异步任务内容
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 操作幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 已尝试执行次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 最大允许执行次数
     */
    @TableField("max_attempts")
    private Integer maxAttempts;

    /**
     * 下次可执行时间
     */
    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    /**
     * 任务锁定时间
     */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /**
     * 当前执行节点标识
     */
    @TableField("locked_by")
    private String lockedBy;

    /**
     * 最近一次失败编码
     */
    @TableField("last_error_code")
    private String lastErrorCode;

    /**
     * 最近一次失败说明
     */
    @TableField("last_error_message")
    private String lastErrorMessage;

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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getNextRunTime() {
        return nextRunTime;
    }

    public void setNextRunTime(LocalDateTime nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
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
        return "FlowJob{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", executionId = " + executionId +
            ", nodeKey = " + nodeKey +
            ", jobType = " + jobType +
            ", status = " + status +
            ", payloadJson = " + payloadJson +
            ", idempotencyKey = " + idempotencyKey +
            ", attemptCount = " + attemptCount +
            ", maxAttempts = " + maxAttempts +
            ", nextRunTime = " + nextRunTime +
            ", lockTime = " + lockTime +
            ", lockedBy = " + lockedBy +
            ", lastErrorCode = " + lastErrorCode +
            ", lastErrorMessage = " + lastErrorMessage +
            ", completeTime = " + completeTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
