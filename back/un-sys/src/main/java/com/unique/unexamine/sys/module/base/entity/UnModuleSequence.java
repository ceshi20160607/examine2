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
 * 模块自动编号并发序列
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_sequence")
public class UnModuleSequence implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块业务编号序列主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 生成编号的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 编号所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 使用的模块编号规则编码
     */
    @TableField("rule_code")
    private String ruleCode;

    /**
     * 按规则重置的日期月份或固定周期键
     */
    @TableField("period_key")
    private String periodKey;

    /**
     * 该作用域已分配最大序号
     */
    @TableField("current_value")
    private Long currentValue;

    /**
     * 序号最近分配时间
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Long currentValue) {
        this.currentValue = currentValue;
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
