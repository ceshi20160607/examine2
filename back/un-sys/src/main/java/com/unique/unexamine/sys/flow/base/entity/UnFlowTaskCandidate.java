package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程待办候选处理人
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_flow_task_candidate")
public class UnFlowTaskCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程待办候选人主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程待办主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 平台账号或应用外部身份类型
     */
    @TableField("candidate_type")
    private String candidateType;

    /**
     * 内部候选平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 应用身份命名空间内外部候选人标识
     */
    @TableField("external_id")
    private String externalId;

    /**
     * 外部候选身份所属受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 候选待领领取完成或移除状态
     */
    @TableField("status")
    private String status;

    /**
     * 候选人领取时间
     */
    @TableField("claim_time")
    private LocalDateTime claimTime;

    /**
     * 候选关系创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 关系更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getClaimTime() {
        return claimTime;
    }

    public void setClaimTime(LocalDateTime claimTime) {
        this.claimTime = claimTime;
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
}
