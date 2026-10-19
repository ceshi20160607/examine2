package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块规则配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_rule")
public class UnModuleRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块规则主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内唯一的规则编码
     */
    @TableField("rule_code")
    private String ruleCode;

    /**
     * 规则名称
     */
    @TableField("name")
    private String name;

    /**
     * 规则触发方式，如保存或执行动作
     */
    @TableField("trigger_type")
    private String triggerType;

    /**
     * 规则生效条件
     */
    @TableField("condition_json")
    private String conditionJson;

    /**
     * 字段校验或业务处理配置
     */
    @TableField("effect_json")
    private String effectJson;

    /**
     * 规则执行顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 规则状态：1 启用，0 停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public String getEffectJson() {
        return effectJson;
    }

    public void setEffectJson(String effectJson) {
        this.effectJson = effectJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
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
