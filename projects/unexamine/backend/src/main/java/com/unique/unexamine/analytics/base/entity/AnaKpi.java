package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 按发布数据源版本计算的 KPI 目标及提醒口径
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_kpi")
public class AnaKpi {

    /**
     * KPI 目标主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 运行上下文，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统 KPI 所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户 KPI 所属租户主键
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 唯一归属层级，PLATFORM、SYSTEM 或 TENANT
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 归属层级对应的平台、系统或租户主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 同一归属层级内稳定的 KPI 编码
     */
    @TableField("`code`")
    private String code;

    /**
     * KPI 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 指标统计口径所属数据源主键
     */
    @TableField("data_source_id")
    private Long dataSourceId;

    /**
     * 固定的已发布数据源版本主键
     */
    @TableField("data_source_version_id")
    private Long dataSourceVersionId;

    /**
     * 被引用数据源版本的归属层级，可为本租户、系统默认或主租户共享
     */
    @TableField("source_scope_type")
    private String sourceScopeType;

    /**
     * 被引用数据源版本的归属主体主键
     */
    @TableField("source_scope_owner_id")
    private Long sourceScopeOwnerId;

    /**
     * 目标对象类型：全部、人员、部门或角色
     */
    @TableField("target_subject_type")
    private String targetSubjectType;

    /**
     * 人员、部门或角色目标对象主键，全部对象时为空
     */
    @TableField("target_subject_id")
    private Long targetSubjectId;

    /**
     * 目标值或计算表达式
     */
    @TableField("target_expression")
    private String targetExpression;

    /**
     * 目标周期，MONTH、QUARTER 或 YEAR
     */
    @TableField("calculation_schedule")
    private String calculationSchedule;

    /**
     * 分组维度、汇总字段与可见范围配置
     */
    @TableField("dimension_json")
    private String dimensionJson;

    /**
     * 未达标提醒策略
     */
    @TableField("reminder_policy_json")
    private String reminderPolicyJson;

    /**
     * 草稿、启用或停用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建者账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

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
     * 乐观锁及计算口径版本号
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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public Long getDataSourceVersionId() {
        return dataSourceVersionId;
    }

    public void setDataSourceVersionId(Long dataSourceVersionId) {
        this.dataSourceVersionId = dataSourceVersionId;
    }

    public String getSourceScopeType() {
        return sourceScopeType;
    }

    public void setSourceScopeType(String sourceScopeType) {
        this.sourceScopeType = sourceScopeType;
    }

    public Long getSourceScopeOwnerId() {
        return sourceScopeOwnerId;
    }

    public void setSourceScopeOwnerId(Long sourceScopeOwnerId) {
        this.sourceScopeOwnerId = sourceScopeOwnerId;
    }

    public String getTargetSubjectType() {
        return targetSubjectType;
    }

    public void setTargetSubjectType(String targetSubjectType) {
        this.targetSubjectType = targetSubjectType;
    }

    public Long getTargetSubjectId() {
        return targetSubjectId;
    }

    public void setTargetSubjectId(Long targetSubjectId) {
        this.targetSubjectId = targetSubjectId;
    }

    public String getTargetExpression() {
        return targetExpression;
    }

    public void setTargetExpression(String targetExpression) {
        this.targetExpression = targetExpression;
    }

    public String getCalculationSchedule() {
        return calculationSchedule;
    }

    public void setCalculationSchedule(String calculationSchedule) {
        this.calculationSchedule = calculationSchedule;
    }

    public String getDimensionJson() {
        return dimensionJson;
    }

    public void setDimensionJson(String dimensionJson) {
        this.dimensionJson = dimensionJson;
    }

    public String getReminderPolicyJson() {
        return reminderPolicyJson;
    }

    public void setReminderPolicyJson(String reminderPolicyJson) {
        this.reminderPolicyJson = reminderPolicyJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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
        return "AnaKpi{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", code = " + code +
            ", name = " + name +
            ", dataSourceId = " + dataSourceId +
            ", dataSourceVersionId = " + dataSourceVersionId +
            ", sourceScopeType = " + sourceScopeType +
            ", sourceScopeOwnerId = " + sourceScopeOwnerId +
            ", targetSubjectType = " + targetSubjectType +
            ", targetSubjectId = " + targetSubjectId +
            ", targetExpression = " + targetExpression +
            ", calculationSchedule = " + calculationSchedule +
            ", dimensionJson = " + dimensionJson +
            ", reminderPolicyJson = " + reminderPolicyJson +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
