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
 * 模块动作及事件流程绑定
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_binding")
public class UnFlowBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程触发绑定主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统绑定作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统流程所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 发布绑定的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 系统模块触发时的模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 动作触发时的系统菜单主键
     */
    @TableField("action_menu_id")
    private Long actionMenuId;

    /**
     * 新建编辑状态变化手动或定时事件编码
     */
    @TableField("event_code")
    private String eventCode;

    /**
     * 目标流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 绑定时已发布流程版本主键
     */
    @TableField("flow_version_id")
    private Long flowVersionId;

    /**
     * 先执行后触发先审批后执行或只启动流程
     */
    @TableField("execution_mode")
    private String executionMode;

    /**
     * 同事件绑定优先级
     */
    @TableField("priority_no")
    private Integer priorityNo;

    /**
     * 命中后是否排斥后续绑定
     */
    @TableField("exclusive_flag")
    private Boolean exclusiveFlag;

    /**
     * 结构化触发条件
     */
    @TableField("condition_json")
    private String conditionJson;

    /**
     * 当前绑定的启停状态
     */
    @TableField("status")
    private String status;

    /**
     * 绑定自身发布版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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

    public Long getActionMenuId() {
        return actionMenuId;
    }

    public void setActionMenuId(Long actionMenuId) {
        this.actionMenuId = actionMenuId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getFlowVersionId() {
        return flowVersionId;
    }

    public void setFlowVersionId(Long flowVersionId) {
        this.flowVersionId = flowVersionId;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public Integer getPriorityNo() {
        return priorityNo;
    }

    public void setPriorityNo(Integer priorityNo) {
        this.priorityNo = priorityNo;
    }

    public Boolean getExclusiveFlag() {
        return exclusiveFlag;
    }

    public void setExclusiveFlag(Boolean exclusiveFlag) {
        this.exclusiveFlag = exclusiveFlag;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
