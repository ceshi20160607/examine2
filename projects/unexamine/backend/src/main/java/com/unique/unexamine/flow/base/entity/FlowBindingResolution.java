package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 触发时的最终生效绑定解析
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_binding_resolution")
public class FlowBindingResolution {

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
     * 实际运行租户ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 关联业务模块ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块业务触发事件编码
     */
    @TableField("trigger_event")
    private String triggerEvent;

    /**
     * 命中的流程绑定ID
     */
    @TableField("binding_id")
    private Long bindingId;

    /**
     * 固定的流程发布版本ID
     */
    @TableField("flow_version_id")
    private Long flowVersionId;

    /**
     * 默认继承或租户覆盖原因
     */
    @TableField("resolution_reason")
    private String resolutionReason;

    /**
     * 绑定解析时间
     */
    @TableField("resolve_time")
    private LocalDateTime resolveTime;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
    }

    public Long getFlowVersionId() {
        return flowVersionId;
    }

    public void setFlowVersionId(Long flowVersionId) {
        this.flowVersionId = flowVersionId;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public void setResolutionReason(String resolutionReason) {
        this.resolutionReason = resolutionReason;
    }

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
    }

    @Override
    public String toString() {
        return "FlowBindingResolution{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", triggerEvent = " + triggerEvent +
            ", bindingId = " + bindingId +
            ", flowVersionId = " + flowVersionId +
            ", resolutionReason = " + resolutionReason +
            ", resolveTime = " + resolveTime +
            "}";
    }
}
