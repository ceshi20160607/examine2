package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 审批流程草稿节点与指定审批人
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_node")
public class FlowNode {

    /**
     * 审批草稿节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 节点展示名称
     */
    @TableField("node_name")
    private String nodeName;

    /**
     * 指定审批人的系统成员主键
     */
    @TableField("approver_member_id")
    private Long approverMemberId;

    /**
     * 节点顺序，当前基础审批只允许一个节点
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 草稿节点创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 草稿节点更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Long getApproverMemberId() {
        return approverMemberId;
    }

    public void setApproverMemberId(Long approverMemberId) {
        this.approverMemberId = approverMemberId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    @Override
    public String toString() {
        return "FlowNode{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", nodeName = " + nodeName +
            ", approverMemberId = " + approverMemberId +
            ", sortOrder = " + sortOrder +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
