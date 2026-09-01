package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("flow_node")
public class FlowNode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_id")
    private Long flowId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("node_type")
    private String nodeType;

    @TableField("`name`")
    private String name;

    @TableField("position_x")
    private BigDecimal positionX;

    @TableField("position_y")
    private BigDecimal positionY;

    @TableField("assignee_policy_json")
    private String assigneePolicyJson;

    @TableField("form_policy_json")
    private String formPolicyJson;

    @TableField("timeout_policy_json")
    private String timeoutPolicyJson;

    @TableField("exception_policy_json")
    private String exceptionPolicyJson;

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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPositionX() {
        return positionX;
    }

    public void setPositionX(BigDecimal positionX) {
        this.positionX = positionX;
    }

    public BigDecimal getPositionY() {
        return positionY;
    }

    public void setPositionY(BigDecimal positionY) {
        this.positionY = positionY;
    }

    public String getAssigneePolicyJson() {
        return assigneePolicyJson;
    }

    public void setAssigneePolicyJson(String assigneePolicyJson) {
        this.assigneePolicyJson = assigneePolicyJson;
    }

    public String getFormPolicyJson() {
        return formPolicyJson;
    }

    public void setFormPolicyJson(String formPolicyJson) {
        this.formPolicyJson = formPolicyJson;
    }

    public String getTimeoutPolicyJson() {
        return timeoutPolicyJson;
    }

    public void setTimeoutPolicyJson(String timeoutPolicyJson) {
        this.timeoutPolicyJson = timeoutPolicyJson;
    }

    public String getExceptionPolicyJson() {
        return exceptionPolicyJson;
    }

    public void setExceptionPolicyJson(String exceptionPolicyJson) {
        this.exceptionPolicyJson = exceptionPolicyJson;
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
        return "FlowNode{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", nodeKey = " + nodeKey +
            ", nodeType = " + nodeType +
            ", name = " + name +
            ", positionX = " + positionX +
            ", positionY = " + positionY +
            ", assigneePolicyJson = " + assigneePolicyJson +
            ", formPolicyJson = " + formPolicyJson +
            ", timeoutPolicyJson = " + timeoutPolicyJson +
            ", exceptionPolicyJson = " + exceptionPolicyJson +
            ", configJson = " + configJson +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
