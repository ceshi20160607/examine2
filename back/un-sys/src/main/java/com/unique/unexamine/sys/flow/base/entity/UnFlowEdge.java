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
 * 流程画布草稿连线
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_edge")
public class UnFlowEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程草稿连线主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 连线起点节点主键
     */
    @TableField("source_node_id")
    private Long sourceNodeId;

    /**
     * 连线终点节点主键
     */
    @TableField("target_node_id")
    private Long targetNodeId;

    /**
     * 结构化条件和分支判断配置
     */
    @TableField("condition_json")
    private String conditionJson;

    /**
     * 同起点分支选择优先级
     */
    @TableField("priority_no")
    private Integer priorityNo;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(Long sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public Long getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(Long targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public Integer getPriorityNo() {
        return priorityNo;
    }

    public void setPriorityNo(Integer priorityNo) {
        this.priorityNo = priorityNo;
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
