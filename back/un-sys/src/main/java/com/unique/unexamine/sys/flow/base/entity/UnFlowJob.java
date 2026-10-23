package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程定时提醒与超时作业
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_job")
public class UnFlowJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程定时作业主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 相关节点待办主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 超时提醒自动处理或等待唤醒类型
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 作业计划执行时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 待执行执行中成功或失败状态
     */
    @TableField("status")
    private String status;

    /**
     * 已尝试执行次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 定时执行稳定幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 任务参数及失败原因
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 作业最终结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 作业创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 作业更新时间
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

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
