package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批实例
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_example")
public class UnExamineExample implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

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
     * 审批发起成员主键
     */
    @TableField("applicant_member_id")
    private Long applicantMemberId;

    /**
     * 发起时审批配置、节点和字段权限快照
     */
    @TableField("definition_snapshot")
    private String definitionSnapshot;

    /**
     * 当前审批节点标识
     */
    @TableField("current_node_key")
    private String currentNodeKey;

    /**
     * 审批状态：PENDING 审批中，APPROVED 已同意，REJECTED 已拒绝，WITHDRAWN 已撤回
     */
    @TableField("status")
    private String status;

    /**
     * 审批提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 审批结束时间
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

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
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

    public Long getApplicantMemberId() {
        return applicantMemberId;
    }

    public void setApplicantMemberId(Long applicantMemberId) {
        this.applicantMemberId = applicantMemberId;
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

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
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
