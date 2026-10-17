package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块事件和动作的租户流程绑定
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_trigger_binding")
public class FlowTriggerBinding {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统ID
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置来源租户ID
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 关联业务模块ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 绑定的模块动作ID
     */
    @TableField("action_id")
    private Long actionId;

    /**
     * 模块业务触发事件编码
     */
    @TableField("trigger_event")
    private String triggerEvent;

    /**
     * 命中的流程定义ID；直接执行业务动作时为空
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 条件或绑定优先级
     */
    @TableField("priority_order")
    private Integer priorityOrder;

    /**
     * 触发或连线条件表达式
     */
    @TableField("condition_expression")
    private String conditionExpression;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getActionId() {
        return actionId;
    }

    public void setActionId(Long actionId) {
        this.actionId = actionId;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getPriorityOrder() {
        return priorityOrder;
    }

    public void setPriorityOrder(Integer priorityOrder) {
        this.priorityOrder = priorityOrder;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        return "FlowTriggerBinding{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", actionId = " + actionId +
            ", triggerEvent = " + triggerEvent +
            ", flowId = " + flowId +
            ", priorityOrder = " + priorityOrder +
            ", conditionExpression = " + conditionExpression +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
