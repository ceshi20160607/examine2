package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 运行流程实例及启动快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_instance")
public class FlowInstance {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程所属上下文类型
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台ID
     */
    @TableField("platform_id")
    private Long platformId;

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
     * 流程定义ID
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 固定的流程发布版本ID
     */
    @TableField("flow_version_id")
    private Long flowVersionId;

    /**
     * 启动时关联的业务模块ID；平台独立流程为空
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 启动时固定的模块发布版本ID
     */
    @TableField("module_version_id")
    private Long moduleVersionId;

    /**
     * 关联的通用业务记录ID；非记录流程为空
     */
    @TableField("business_record_id")
    private Long businessRecordId;

    /**
     * 本次触发绑定解析记录ID；独立发起为空
     */
    @TableField("binding_resolution_id")
    private Long bindingResolutionId;

    /**
     * 本次命中的流程绑定ID；独立发起为空
     */
    @TableField("binding_id")
    private Long bindingId;

    /**
     * 绑定来源租户ID，可与实例实际租户不同
     */
    @TableField("binding_source_tenant_id")
    private Long bindingSourceTenantId;

    /**
     * 关联业务对象类型
     */
    @TableField("business_type")
    private String businessType;

    /**
     * 关联业务对象外部标识
     */
    @TableField("business_id")
    private String businessId;

    /**
     * 启动时业务对象数据快照
     */
    @TableField("business_snapshot_json")
    private String businessSnapshotJson;

    /**
     * 启动时复制的节点、连线、条件、字段权限及高级设置
     */
    @TableField("runtime_snapshot_json")
    private String runtimeSnapshotJson;

    /**
     * 流程实例标题
     */
    @TableField("title")
    private String title;

    /**
     * 当前运行节点编码
     */
    @TableField("current_node_key")
    private String currentNodeKey;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 发起人平台账号ID
     */
    @TableField("started_by_account_id")
    private Long startedByAccountId;

    /**
     * 发起人在实际运行租户的成员ID
     */
    @TableField("started_by_tenant_member_id")
    private Long startedByTenantMemberId;

    /**
     * 外部发起来源应用ID；内部发起为空
     */
    @TableField("source_application_id")
    private Long sourceApplicationId;

    /**
     * 应用命名空间内的外部发起人标识
     */
    @TableField("source_external_principal_id")
    private String sourceExternalPrincipalId;

    /**
     * 流程启动时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 流程结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 失败错误编码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 失败原因说明
     */
    @TableField("error_message")
    private String errorMessage;

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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getModuleVersionId() {
        return moduleVersionId;
    }

    public void setModuleVersionId(Long moduleVersionId) {
        this.moduleVersionId = moduleVersionId;
    }

    public Long getBusinessRecordId() {
        return businessRecordId;
    }

    public void setBusinessRecordId(Long businessRecordId) {
        this.businessRecordId = businessRecordId;
    }

    public Long getBindingResolutionId() {
        return bindingResolutionId;
    }

    public void setBindingResolutionId(Long bindingResolutionId) {
        this.bindingResolutionId = bindingResolutionId;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
    }

    public Long getBindingSourceTenantId() {
        return bindingSourceTenantId;
    }

    public void setBindingSourceTenantId(Long bindingSourceTenantId) {
        this.bindingSourceTenantId = bindingSourceTenantId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getBusinessSnapshotJson() {
        return businessSnapshotJson;
    }

    public void setBusinessSnapshotJson(String businessSnapshotJson) {
        this.businessSnapshotJson = businessSnapshotJson;
    }

    public String getRuntimeSnapshotJson() {
        return runtimeSnapshotJson;
    }

    public void setRuntimeSnapshotJson(String runtimeSnapshotJson) {
        this.runtimeSnapshotJson = runtimeSnapshotJson;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCurrentNodeKey() {
        return currentNodeKey;
    }

    public void setCurrentNodeKey(String currentNodeKey) {
        this.currentNodeKey = currentNodeKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getStartedByAccountId() {
        return startedByAccountId;
    }

    public void setStartedByAccountId(Long startedByAccountId) {
        this.startedByAccountId = startedByAccountId;
    }

    public Long getStartedByTenantMemberId() {
        return startedByTenantMemberId;
    }

    public void setStartedByTenantMemberId(Long startedByTenantMemberId) {
        this.startedByTenantMemberId = startedByTenantMemberId;
    }

    public Long getSourceApplicationId() {
        return sourceApplicationId;
    }

    public void setSourceApplicationId(Long sourceApplicationId) {
        this.sourceApplicationId = sourceApplicationId;
    }

    public String getSourceExternalPrincipalId() {
        return sourceExternalPrincipalId;
    }

    public void setSourceExternalPrincipalId(String sourceExternalPrincipalId) {
        this.sourceExternalPrincipalId = sourceExternalPrincipalId;
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
        return "FlowInstance{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", flowId = " + flowId +
            ", flowVersionId = " + flowVersionId +
            ", moduleId = " + moduleId +
            ", moduleVersionId = " + moduleVersionId +
            ", businessRecordId = " + businessRecordId +
            ", bindingResolutionId = " + bindingResolutionId +
            ", bindingId = " + bindingId +
            ", bindingSourceTenantId = " + bindingSourceTenantId +
            ", businessType = " + businessType +
            ", businessId = " + businessId +
            ", businessSnapshotJson = " + businessSnapshotJson +
            ", runtimeSnapshotJson = " + runtimeSnapshotJson +
            ", title = " + title +
            ", currentNodeKey = " + currentNodeKey +
            ", status = " + status +
            ", startedByAccountId = " + startedByAccountId +
            ", startedByTenantMemberId = " + startedByTenantMemberId +
            ", sourceApplicationId = " + sourceApplicationId +
            ", sourceExternalPrincipalId = " + sourceExternalPrincipalId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
