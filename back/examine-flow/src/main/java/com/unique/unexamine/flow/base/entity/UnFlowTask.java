package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 节点执行轮次的待办任务
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_task")
public class UnFlowTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程待办任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 执行节点标识
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 同一节点的执行轮次
     */
    @TableField("round_no")
    private Integer roundNo;

    /**
     * 任务状态：PENDING 待处理，COMPLETED 已处理，CANCELLED 已取消，EXPIRED 已超时
     */
    @TableField("status")
    private String status;

    /**
     * 认领该任务的成员主键
     */
    @TableField("claim_member_id")
    private Long claimMemberId;

    /**
     * 任务分配时间
     */
    @TableField("assign_time")
    private LocalDateTime assignTime;

    /**
     * 任务认领时间
     */
    @TableField("claim_time")
    private LocalDateTime claimTime;

    /**
     * 任务处理期限
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 任务完成或关闭时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getClaimMemberId() {
        return claimMemberId;
    }

    public void setClaimMemberId(Long claimMemberId) {
        this.claimMemberId = claimMemberId;
    }

    public LocalDateTime getAssignTime() {
        return assignTime;
    }

    public void setAssignTime(LocalDateTime assignTime) {
        this.assignTime = assignTime;
    }

    public LocalDateTime getClaimTime() {
        return claimTime;
    }

    public void setClaimTime(LocalDateTime claimTime) {
        this.claimTime = claimTime;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
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
}
