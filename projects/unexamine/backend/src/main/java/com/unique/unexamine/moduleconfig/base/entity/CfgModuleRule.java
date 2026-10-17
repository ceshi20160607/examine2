package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块规则草稿配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module_rule")
public class CfgModuleRule {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 所属模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 范围内稳定编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 规则业务类型
     */
    @TableField("rule_type")
    private String ruleType;

    /**
     * 规则触发事件
     */
    @TableField("trigger_event")
    private String triggerEvent;

    /**
     * 规则表达式文本
     */
    @TableField("expression_text")
    private String expressionText;

    /**
     * 规则失败提示模板
     */
    @TableField("message_template")
    private String messageTemplate;

    /**
     * 草稿规则最近一次测试状态
     */
    @TableField("test_status")
    private String testStatus;

    /**
     * 最近一次规则测试输入快照
     */
    @TableField("last_test_input_json")
    private String lastTestInputJson;

    /**
     * 最近一次规则测试结果快照
     */
    @TableField("last_test_result_json")
    private String lastTestResultJson;

    /**
     * 最近一次执行测试的系统成员标识
     */
    @TableField("last_tested_by_member_id")
    private Long lastTestedByMemberId;

    /**
     * 最近一次规则测试时间
     */
    @TableField("last_tested_time")
    private LocalDateTime lastTestedTime;

    /**
     * 显示排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
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

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public String getExpressionText() {
        return expressionText;
    }

    public void setExpressionText(String expressionText) {
        this.expressionText = expressionText;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public String getTestStatus() {
        return testStatus;
    }

    public void setTestStatus(String testStatus) {
        this.testStatus = testStatus;
    }

    public String getLastTestInputJson() {
        return lastTestInputJson;
    }

    public void setLastTestInputJson(String lastTestInputJson) {
        this.lastTestInputJson = lastTestInputJson;
    }

    public String getLastTestResultJson() {
        return lastTestResultJson;
    }

    public void setLastTestResultJson(String lastTestResultJson) {
        this.lastTestResultJson = lastTestResultJson;
    }

    public Long getLastTestedByMemberId() {
        return lastTestedByMemberId;
    }

    public void setLastTestedByMemberId(Long lastTestedByMemberId) {
        this.lastTestedByMemberId = lastTestedByMemberId;
    }

    public LocalDateTime getLastTestedTime() {
        return lastTestedTime;
    }

    public void setLastTestedTime(LocalDateTime lastTestedTime) {
        this.lastTestedTime = lastTestedTime;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "CfgModuleRule{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", ruleType = " + ruleType +
            ", triggerEvent = " + triggerEvent +
            ", expressionText = " + expressionText +
            ", messageTemplate = " + messageTemplate +
            ", testStatus = " + testStatus +
            ", lastTestInputJson = " + lastTestInputJson +
            ", lastTestResultJson = " + lastTestResultJson +
            ", lastTestedByMemberId = " + lastTestedByMemberId +
            ", lastTestedTime = " + lastTestedTime +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
