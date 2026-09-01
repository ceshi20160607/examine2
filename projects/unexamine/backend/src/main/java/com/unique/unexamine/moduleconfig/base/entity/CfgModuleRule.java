package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("cfg_module_rule")
public class CfgModuleRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("rule_type")
    private String ruleType;

    @TableField("trigger_event")
    private String triggerEvent;

    @TableField("expression_text")
    private String expressionText;

    @TableField("message_template")
    private String messageTemplate;

    @TableField("test_status")
    private String testStatus;

    @TableField("last_test_input_json")
    private String lastTestInputJson;

    @TableField("last_test_result_json")
    private String lastTestResultJson;

    @TableField("last_tested_by_member_id")
    private Long lastTestedByMemberId;

    @TableField("last_tested_at")
    private LocalDateTime lastTestedAt;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("`status`")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public LocalDateTime getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(LocalDateTime lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
            ", lastTestedAt = " + lastTestedAt +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
