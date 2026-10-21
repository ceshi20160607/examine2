package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程草稿节点
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_node")
public class UnFlowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程草稿节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程内稳定节点标识
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 节点名称
     */
    @TableField("name")
    private String name;

    /**
     * 节点类型：START 开始，TASK 人工任务，CONDITION 条件，END 结束
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 人工任务处理人来源：MEMBER 成员，ROLE 角色，STARTER 发起人
     */
    @TableField("assignee_type")
    private String assigneeType;

    /**
     * 固定成员或角色主键，依处理人来源解释
     */
    @TableField("assignee_id")
    private Long assigneeId;

    /**
     * 草稿展示顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
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
