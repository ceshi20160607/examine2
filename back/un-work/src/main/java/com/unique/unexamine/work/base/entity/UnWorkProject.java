package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作项目
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_work_project")
public class UnWorkProject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作项目主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统工作作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统项目所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统项目所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作用域内项目编号
     */
    @TableField("code")
    private String code;

    /**
     * 项目名称
     */
    @TableField("name")
    private String name;

    /**
     * 项目业务说明
     */
    @TableField("description")
    private String description;

    /**
     * 项目负责人平台账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 项目归属部门主键
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 关联业务模块主键
     */
    @TableField("related_module_id")
    private Long relatedModuleId;

    /**
     * 关联业务记录主键
     */
    @TableField("related_record_id")
    private Long relatedRecordId;

    /**
     * 项目当前阶段字典值标识
     */
    @TableField("stage_code")
    private String stageCode;

    /**
     * 项目进行完成归档等状态
     */
    @TableField("status")
    private String status;

    /**
     * 项目完成进度百分比
     */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

    /**
     * 项目开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 项目计划截止时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 项目引用的标签字典项主键集合
     */
    @TableField("tag_json")
    private String tagJson;

    /**
     * 创建项目的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getRelatedModuleId() {
        return relatedModuleId;
    }

    public void setRelatedModuleId(Long relatedModuleId) {
        this.relatedModuleId = relatedModuleId;
    }

    public Long getRelatedRecordId() {
        return relatedRecordId;
    }

    public void setRelatedRecordId(Long relatedRecordId) {
        this.relatedRecordId = relatedRecordId;
    }

    public String getStageCode() {
        return stageCode;
    }

    public void setStageCode(String stageCode) {
        this.stageCode = stageCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public String getTagJson() {
        return tagJson;
    }

    public void setTagJson(String tagJson) {
        this.tagJson = tagJson;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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
