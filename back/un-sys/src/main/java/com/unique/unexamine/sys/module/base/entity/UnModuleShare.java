package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 跨租户业务数据显式共享
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_share")
public class UnModuleShare implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 跨租户业务数据共享主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 来源与目标共同所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务数据原始所属租户主键
     */
    @TableField("source_tenant_id")
    private Long sourceTenantId;

    /**
     * 获授权访问的目标租户主键
     */
    @TableField("target_tenant_id")
    private Long targetTenantId;

    /**
     * 共享业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 指定单条共享记录主键，范围共享时为空
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 按负责人和结构化条件配置的共享记录范围
     */
    @TableField("scope_json")
    private String scopeJson;

    /**
     * 单独允许的详情编辑导出等操作菜单主键
     */
    @TableField("action_menu_ids_json")
    private String actionMenuIdsJson;

    /**
     * 共享生效停用或已撤销状态
     */
    @TableField("status")
    private String status;

    /**
     * 共享开始生效时间
     */
    @TableField("effective_time")
    private LocalDateTime effectiveTime;

    /**
     * 共享到期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 来源租户创建共享的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

    /**
     * 实际撤销共享的平台账号主键
     */
    @TableField("revoke_account_id")
    private Long revokeAccountId;

    /**
     * 共享撤销时间
     */
    @TableField("revoke_time")
    private LocalDateTime revokeTime;

    /**
     * 共享创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 共享更新时间
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getSourceTenantId() {
        return sourceTenantId;
    }

    public void setSourceTenantId(Long sourceTenantId) {
        this.sourceTenantId = sourceTenantId;
    }

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
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

    public String getScopeJson() {
        return scopeJson;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public String getActionMenuIdsJson() {
        return actionMenuIdsJson;
    }

    public void setActionMenuIdsJson(String actionMenuIdsJson) {
        this.actionMenuIdsJson = actionMenuIdsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEffectiveTime() {
        return effectiveTime;
    }

    public void setEffectiveTime(LocalDateTime effectiveTime) {
        this.effectiveTime = effectiveTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
    }

    public Long getRevokeAccountId() {
        return revokeAccountId;
    }

    public void setRevokeAccountId(Long revokeAccountId) {
        this.revokeAccountId = revokeAccountId;
    }

    public LocalDateTime getRevokeTime() {
        return revokeTime;
    }

    public void setRevokeTime(LocalDateTime revokeTime) {
        this.revokeTime = revokeTime;
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
