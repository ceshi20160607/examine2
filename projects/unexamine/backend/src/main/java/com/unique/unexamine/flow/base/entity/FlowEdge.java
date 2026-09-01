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
@TableName("flow_edge")
public class FlowEdge {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_id")
    private Long flowId;

    @TableField("edge_key")
    private String edgeKey;

    @TableField("source_node_key")
    private String sourceNodeKey;

    @TableField("target_node_key")
    private String targetNodeKey;

    @TableField("condition_expression")
    private String conditionExpression;

    @TableField("priority_order")
    private Integer priorityOrder;

    @TableField("config_json")
    private String configJson;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getEdgeKey() {
        return edgeKey;
    }

    public void setEdgeKey(String edgeKey) {
        this.edgeKey = edgeKey;
    }

    public String getSourceNodeKey() {
        return sourceNodeKey;
    }

    public void setSourceNodeKey(String sourceNodeKey) {
        this.sourceNodeKey = sourceNodeKey;
    }

    public String getTargetNodeKey() {
        return targetNodeKey;
    }

    public void setTargetNodeKey(String targetNodeKey) {
        this.targetNodeKey = targetNodeKey;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public Integer getPriorityOrder() {
        return priorityOrder;
    }

    public void setPriorityOrder(Integer priorityOrder) {
        this.priorityOrder = priorityOrder;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
        return "FlowEdge{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", edgeKey = " + edgeKey +
            ", sourceNodeKey = " + sourceNodeKey +
            ", targetNodeKey = " + targetNodeKey +
            ", conditionExpression = " + conditionExpression +
            ", priorityOrder = " + priorityOrder +
            ", configJson = " + configJson +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
