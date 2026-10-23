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
 * 模块变动记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_log")
public class UnModuleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块变动日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务记录或配置所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 实际操作人的当前租户主键
     */
    @TableField("actor_tenant_id")
    private Long actorTenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 变动业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 跨租户操作使用的共享授权主键
     */
    @TableField("share_id")
    private Long shareId;

    /**
     * 实际操作平台账号主键
     */
    @TableField("operator_account_id")
    private Long operatorAccountId;

    /**
     * 配置发布或记录操作类型
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 变动字段与业务上下文摘要
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 操作发生时的菜单字段及数据权限摘要
     */
    @TableField("permission_json")
    private String permissionJson;

    /**
     * 操作时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getActorTenantId() {
        return actorTenantId;
    }

    public void setActorTenantId(Long actorTenantId) {
        this.actorTenantId = actorTenantId;
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

    public Long getShareId() {
        return shareId;
    }

    public void setShareId(Long shareId) {
        this.shareId = shareId;
    }

    public Long getOperatorAccountId() {
        return operatorAccountId;
    }

    public void setOperatorAccountId(Long operatorAccountId) {
        this.operatorAccountId = operatorAccountId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public String getPermissionJson() {
        return permissionJson;
    }

    public void setPermissionJson(String permissionJson) {
        this.permissionJson = permissionJson;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
