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
 * 保存运行快照的流程实例
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_example")
public class UnFlowExample implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 运行流程实例主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 实际运行流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 实际运行不可变版本主键
     */
    @TableField("flow_version_id")
    private Long flowVersionId;

    /**
     * 命中流程绑定主键
     */
    @TableField("binding_id")
    private Long bindingId;

    /**
     * 平台或系统实例作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统实例所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 实例所属实际业务租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 继承主租户流程时的配置来源租户主键
     */
    @TableField("source_tenant_id")
    private Long sourceTenantId;

    /**
     * 关联业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 关联业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 外部发起所用受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 内部发起平台账号主键
     */
    @TableField("initiator_account_id")
    private Long initiatorAccountId;

    /**
     * 外部发起人命名空间内标识
     */
    @TableField("initiator_external_id")
    private String initiatorExternalId;

    /**
     * Flow 运行中、完成或异常状态；审批结果保存在 examine 实例并作为 Flow 事件使用
     */
    @TableField("status")
    private String status;

    /**
     * 启动时流程图字段权限绑定和业务值快照
     */
    @TableField("runtime_snapshot_json")
    private String runtimeSnapshotJson;

    /**
     * 并行运行中节点编码集合
     */
    @TableField("current_nodes_json")
    private String currentNodesJson;

    /**
     * 实例启动时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 实例结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 实例更新时间
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

    public Long getFlowVersionId() {
        return flowVersionId;
    }

    public void setFlowVersionId(Long flowVersionId) {
        this.flowVersionId = flowVersionId;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
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

    public Long getSourceTenantId() {
        return sourceTenantId;
    }

    public void setSourceTenantId(Long sourceTenantId) {
        this.sourceTenantId = sourceTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getInitiatorAccountId() {
        return initiatorAccountId;
    }

    public void setInitiatorAccountId(Long initiatorAccountId) {
        this.initiatorAccountId = initiatorAccountId;
    }

    public String getInitiatorExternalId() {
        return initiatorExternalId;
    }

    public void setInitiatorExternalId(String initiatorExternalId) {
        this.initiatorExternalId = initiatorExternalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuntimeSnapshotJson() {
        return runtimeSnapshotJson;
    }

    public void setRuntimeSnapshotJson(String runtimeSnapshotJson) {
        this.runtimeSnapshotJson = runtimeSnapshotJson;
    }

    public String getCurrentNodesJson() {
        return currentNodesJson;
    }

    public void setCurrentNodesJson(String currentNodesJson) {
        this.currentNodesJson = currentNodesJson;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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
