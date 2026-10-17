package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程草稿连线
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_edge")
public class FlowEdge {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程定义ID
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程连线稳定编码
     */
    @TableField("edge_key")
    private String edgeKey;

    /**
     * 连线起点节点编码
     */
    @TableField("source_node_key")
    private String sourceNodeKey;

    /**
     * 连线终点节点编码
     */
    @TableField("target_node_key")
    private String targetNodeKey;

    /**
     * 触发或连线条件表达式
     */
    @TableField("condition_expression")
    private String conditionExpression;

    /**
     * 条件或绑定优先级
     */
    @TableField("priority_order")
    private Integer priorityOrder;

    /**
     * 节点或连线业务配置
     */
    @TableField("config_json")
    private String configJson;

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
        return "FlowEdge{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", edgeKey = " + edgeKey +
            ", sourceNodeKey = " + sourceNodeKey +
            ", targetNodeKey = " + targetNodeKey +
            ", conditionExpression = " + conditionExpression +
            ", priorityOrder = " + priorityOrder +
            ", configJson = " + configJson +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
