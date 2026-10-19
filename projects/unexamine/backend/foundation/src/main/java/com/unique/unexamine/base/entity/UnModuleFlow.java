package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块流程
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_flow")
public class UnModuleFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块流程绑定主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 适用租户主键，0 为模块默认绑定
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 所绑定的流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 模块内流程绑定编码
     */
    @TableField("binding_code")
    private String bindingCode;

    /**
     * 触发流程的模块动作编码
     */
    @TableField("start_action_code")
    private String startActionCode;

    /**
     * 绑定状态：1 启用，0 停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getBindingCode() {
        return bindingCode;
    }

    public void setBindingCode(String bindingCode) {
        this.bindingCode = bindingCode;
    }

    public String getStartActionCode() {
        return startActionCode;
    }

    public void setStartActionCode(String startActionCode) {
        this.startActionCode = startActionCode;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
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
}
