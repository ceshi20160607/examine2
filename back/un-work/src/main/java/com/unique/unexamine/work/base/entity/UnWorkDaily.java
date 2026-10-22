package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户手动创建的每日工作日志及版本
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_work_daily")
public class UnWorkDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 每日工作日志版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 同一日志首版主键，首版自身为空
     */
    @TableField("root_id")
    private Long rootId;

    /**
     * 手动提交撤回或补写形成的版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 平台或系统工作日志作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统工作日志所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统工作日志所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 工作日志所有人平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 用户选择的工作日期
     */
    @TableField("log_date")
    private LocalDate logDate;

    /**
     * 今日已完成内容
     */
    @TableField("completed_text")
    private String completedText;

    /**
     * 正在进行内容
     */
    @TableField("ongoing_text")
    private String ongoingText;

    /**
     * 明日计划内容
     */
    @TableField("next_text")
    private String nextText;

    /**
     * 问题风险内容
     */
    @TableField("risk_text")
    private String riskText;

    /**
     * 需要协助内容
     */
    @TableField("help_text")
    private String helpText;

    /**
     * 用户主动引用的任务项目业务对象和附件
     */
    @TableField("relation_json")
    private String relationJson;

    /**
     * 已发布日志字段配置对应的值
     */
    @TableField("field_values_json")
    private String fieldValuesJson;

    /**
     * 仅自己上级项目团队或角色可见范围
     */
    @TableField("visibility_json")
    private String visibilityJson;

    /**
     * 草稿提交或撤回状态
     */
    @TableField("status")
    private String status;

    /**
     * 是否当前有效展示版本
     */
    @TableField("is_current")
    private Boolean isCurrent;

    /**
     * 本版本提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 本版本创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 本版本更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRootId() {
        return rootId;
    }

    public void setRootId(Long rootId) {
        this.rootId = rootId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public String getCompletedText() {
        return completedText;
    }

    public void setCompletedText(String completedText) {
        this.completedText = completedText;
    }

    public String getOngoingText() {
        return ongoingText;
    }

    public void setOngoingText(String ongoingText) {
        this.ongoingText = ongoingText;
    }

    public String getNextText() {
        return nextText;
    }

    public void setNextText(String nextText) {
        this.nextText = nextText;
    }

    public String getRiskText() {
        return riskText;
    }

    public void setRiskText(String riskText) {
        this.riskText = riskText;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public String getRelationJson() {
        return relationJson;
    }

    public void setRelationJson(String relationJson) {
        this.relationJson = relationJson;
    }

    public String getFieldValuesJson() {
        return fieldValuesJson;
    }

    public void setFieldValuesJson(String fieldValuesJson) {
        this.fieldValuesJson = fieldValuesJson;
    }

    public String getVisibilityJson() {
        return visibilityJson;
    }

    public void setVisibilityJson(String visibilityJson) {
        this.visibilityJson = visibilityJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsCurrent() {
        return isCurrent;
    }

    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
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
