package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块动作草稿配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module_action")
public class ModuleAction {

    /**
     * 模块动作标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 动作配置来源租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内稳定动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 动作显示名称
     */
    @TableField("action_name")
    private String actionName;

    /**
     * 模块入口、工具栏、行或详情中的展示位置
     */
    @TableField("display_location")
    private String displayLocation;

    /**
     * 同位置动作排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 动作草稿状态：1启用，0停用
     */
    @TableField("`status`")
    private Boolean status;

    /**
     * 动作创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 动作修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getDisplayLocation() {
        return displayLocation;
    }

    public void setDisplayLocation(String displayLocation) {
        this.displayLocation = displayLocation;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
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

    @Override
    public String toString() {
        return "ModuleAction{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", actionCode = " + actionCode +
            ", actionName = " + actionName +
            ", displayLocation = " + displayLocation +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
