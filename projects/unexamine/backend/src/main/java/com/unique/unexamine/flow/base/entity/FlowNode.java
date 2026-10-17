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
 * 流程草稿节点
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_node")
public class FlowNode {

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
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 节点类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 设计画布横坐标
     */
    @TableField("position_x")
    private BigDecimal positionX;

    /**
     * 设计画布纵坐标
     */
    @TableField("position_y")
    private BigDecimal positionY;

    /**
     * 审批人解析策略
     */
    @TableField("assignee_policy_json")
    private String assigneePolicyJson;

    /**
     * 节点表单与字段权限策略
     */
    @TableField("form_policy_json")
    private String formPolicyJson;

    /**
     * 节点超时处理策略
     */
    @TableField("timeout_policy_json")
    private String timeoutPolicyJson;

    /**
     * 节点异常兜底策略
     */
    @TableField("exception_policy_json")
    private String exceptionPolicyJson;

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
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
