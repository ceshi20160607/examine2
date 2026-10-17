package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 不可覆盖的 KPI 计算批次与重算历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_kpi_result")
public class AnaKpiResult {

    /**
     * 一次 KPI 维度计算结果主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属 KPI 目标主键
     */
    @TableField("kpi_id")
    private Long kpiId;

    /**
     * 计算结果归属层级，与 KPI 一致
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 计算结果归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 月、季度或年的周期标识
     */
    @TableField("period_key")
    private String periodKey;

    /**
     * 人员、部门、角色或分组维度稳定键
     */
    @TableField("dimension_key")
    private String dimensionKey;

    /**
     * 同一次重算中各维度共享的计算批次键
     */
    @TableField("calculation_batch_key")
    private String calculationBatchKey;

    /**
     * 同一周期和维度递增的计算修订号，最大号为当前结果
     */
    @TableField("calculation_revision")
    private Integer calculationRevision;

    /**
     * 计算时固定的 KPI 配置乐观锁版本
     */
    @TableField("kpi_config_version")
    private Integer kpiConfigVersion;

    /**
     * 本次计算实际使用的数据源发布版本主键
     */
    @TableField("data_source_version_id")
    private Long dataSourceVersionId;

    /**
     * 本次计算数据源版本的归属层级，可与 KPI 扩展归属不同
     */
    @TableField("source_scope_type")
    private String sourceScopeType;

    /**
     * 本次计算数据源版本的归属主体主键
     */
    @TableField("source_scope_owner_id")
    private Long sourceScopeOwnerId;

    /**
     * 根据模块数据统计出的实际值
     */
    @TableField("actual_value")
    private BigDecimal actualValue;

    /**
     * 本次计算采用的目标值
     */
    @TableField("target_value")
    private BigDecimal targetValue;

    /**
     * 本次计算得到的达成率
     */
    @TableField("achievement_rate")
    private BigDecimal achievementRate;

    /**
     * 达标、未达标或计算失败状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 来源模块、筛选、公式、样本与失败原因快照
     */
    @TableField("explanation_json")
    private String explanationJson;

    /**
     * 实际计算时间
     */
    @TableField("calculate_time")
    private LocalDateTime calculateTime;

    /**
     * 计算记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKpiId() {
        return kpiId;
    }

    public void setKpiId(Long kpiId) {
        this.kpiId = kpiId;
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

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }

    public void setDimensionKey(String dimensionKey) {
        this.dimensionKey = dimensionKey;
    }

    public String getCalculationBatchKey() {
        return calculationBatchKey;
    }

    public void setCalculationBatchKey(String calculationBatchKey) {
        this.calculationBatchKey = calculationBatchKey;
    }

    public Integer getCalculationRevision() {
        return calculationRevision;
    }

    public void setCalculationRevision(Integer calculationRevision) {
        this.calculationRevision = calculationRevision;
    }

    public Integer getKpiConfigVersion() {
        return kpiConfigVersion;
    }

    public void setKpiConfigVersion(Integer kpiConfigVersion) {
        this.kpiConfigVersion = kpiConfigVersion;
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

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getAchievementRate() {
        return achievementRate;
    }

    public void setAchievementRate(BigDecimal achievementRate) {
        this.achievementRate = achievementRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExplanationJson() {
        return explanationJson;
    }

    public void setExplanationJson(String explanationJson) {
        this.explanationJson = explanationJson;
    }

    public LocalDateTime getCalculateTime() {
        return calculateTime;
    }

    public void setCalculateTime(LocalDateTime calculateTime) {
        this.calculateTime = calculateTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AnaKpiResult{" +
            "id = " + id +
            ", kpiId = " + kpiId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", periodKey = " + periodKey +
            ", dimensionKey = " + dimensionKey +
            ", calculationBatchKey = " + calculationBatchKey +
            ", calculationRevision = " + calculationRevision +
            ", kpiConfigVersion = " + kpiConfigVersion +
            ", dataSourceVersionId = " + dataSourceVersionId +
            ", sourceScopeType = " + sourceScopeType +
            ", sourceScopeOwnerId = " + sourceScopeOwnerId +
            ", actualValue = " + actualValue +
            ", targetValue = " + targetValue +
            ", achievementRate = " + achievementRate +
            ", status = " + status +
            ", explanationJson = " + explanationJson +
            ", calculateTime = " + calculateTime +
            ", createTime = " + createTime +
            "}";
    }
}
