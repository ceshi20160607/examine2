package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ana_kpi_result")
public class AnaKpiResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("kpi_id")
    private Long kpiId;

    @TableField("period_key")
    private String periodKey;

    @TableField("dimension_key")
    private String dimensionKey;

    @TableField("actual_value")
    private BigDecimal actualValue;

    @TableField("target_value")
    private BigDecimal targetValue;

    @TableField("achievement_rate")
    private BigDecimal achievementRate;

    @TableField("`status`")
    private String status;

    @TableField("explanation_json")
    private String explanationJson;

    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AnaKpiResult{" +
            "id = " + id +
            ", kpiId = " + kpiId +
            ", periodKey = " + periodKey +
            ", dimensionKey = " + dimensionKey +
            ", actualValue = " + actualValue +
            ", targetValue = " + targetValue +
            ", achievementRate = " + achievementRate +
            ", status = " + status +
            ", explanationJson = " + explanationJson +
            ", calculatedAt = " + calculatedAt +
            ", createdAt = " + createdAt +
            "}";
    }
}
