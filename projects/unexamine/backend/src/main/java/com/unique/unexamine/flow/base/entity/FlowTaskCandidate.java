package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 待办任务候选处理人
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_task_candidate")
public class FlowTaskCandidate {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程待办任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 候选主体类型
     */
    @TableField("candidate_type")
    private String candidateType;

    /**
     * 候选主体标识
     */
    @TableField("candidate_id")
    private String candidateId;

    /**
     * 默认继承或租户覆盖原因
     */
    @TableField("resolution_reason")
    private String resolutionReason;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public void setResolutionReason(String resolutionReason) {
        this.resolutionReason = resolutionReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FlowTaskCandidate{" +
            "id = " + id +
            ", taskId = " + taskId +
            ", candidateType = " + candidateType +
            ", candidateId = " + candidateId +
            ", resolutionReason = " + resolutionReason +
            ", createTime = " + createTime +
            "}";
    }
}
