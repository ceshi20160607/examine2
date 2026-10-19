package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批实例节点的实际处理人员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_example_node_user")
public class UnExamineExampleNodeUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例节点人员主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批实例节点主键
     */
    @TableField("example_node_id")
    private Long exampleNodeId;

    /**
     * 实际处理成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 依次审批时的处理顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

    /**
     * 处理状态：PENDING 待处理，APPROVED 已同意，REJECTED 已拒绝，CANCELLED 已取消
     */
    @TableField("status")
    private String status;

    /**
     * 处理意见
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 本次处理的幂等请求标识
     */
    @TableField("request_key")
    private String requestKey;

    /**
     * 分配给处理人的时间
     */
    @TableField("assign_time")
    private LocalDateTime assignTime;

    /**
     * 实际处理时间
     */
    @TableField("action_time")
    private LocalDateTime actionTime;

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

    public Long getExampleNodeId() {
        return exampleNodeId;
    }

    public void setExampleNodeId(Long exampleNodeId) {
        this.exampleNodeId = exampleNodeId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public LocalDateTime getAssignTime() {
        return assignTime;
    }

    public void setAssignTime(LocalDateTime assignTime) {
        this.assignTime = assignTime;
    }

    public LocalDateTime getActionTime() {
        return actionTime;
    }

    public void setActionTime(LocalDateTime actionTime) {
        this.actionTime = actionTime;
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
