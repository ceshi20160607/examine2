package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块审批关联配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_examine")
public class UnModuleExamine implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块审批配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 审批绑定定义租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 关联业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 关联审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 手动发起审批操作菜单主键
     */
    @TableField("action_menu_id")
    private Long actionMenuId;

    /**
     * 新建编辑状态变动等触发事件编码
     */
    @TableField("trigger_code")
    private String triggerCode;

    /**
     * 结构化审批触发条件
     */
    @TableField("condition_json")
    private String conditionJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

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

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
    }

    public Long getActionMenuId() {
        return actionMenuId;
    }

    public void setActionMenuId(Long actionMenuId) {
        this.actionMenuId = actionMenuId;
    }

    public String getTriggerCode() {
        return triggerCode;
    }

    public void setTriggerCode(String triggerCode) {
        this.triggerCode = triggerCode;
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
