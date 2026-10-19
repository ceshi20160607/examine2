package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 运行流程实例
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_example")
public class UnFlowExample implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程实例主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 发起时的发布版本主键
     */
    @TableField("version_id")
    private Long versionId;

    /**
     * 触发本实例的流程绑定主键
     */
    @TableField("binding_id")
    private Long bindingId;

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
     * 流程发起成员主键
     */
    @TableField("starter_member_id")
    private Long starterMemberId;

    /**
     * 实例发起时的流程定义快照
     */
    @TableField("definition_snapshot")
    private String definitionSnapshot;

    /**
     * 当前执行节点标识
     */
    @TableField("current_node_key")
    private String currentNodeKey;

    /**
     * 实例状态：RUNNING 运行中，COMPLETED 已完成，REJECTED 已拒绝，WITHDRAWN 已撤回，FAILED 异常
     */
    @TableField("status")
    private String status;

    /**
     * 流程发起时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 流程结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
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

    public Long getStarterMemberId() {
        return starterMemberId;
    }

    public void setStarterMemberId(Long starterMemberId) {
        this.starterMemberId = starterMemberId;
    }

    public String getDefinitionSnapshot() {
        return definitionSnapshot;
    }

    public void setDefinitionSnapshot(String definitionSnapshot) {
        this.definitionSnapshot = definitionSnapshot;
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
