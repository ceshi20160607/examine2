package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 指标人员组织目标与计算结果
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_kpi_user")
public class UnKpiUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 指标目标与计算结果主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属关键指标主键
     */
    @TableField("kpi_id")
    private Long kpiId;

    /**
     * 人员部门或角色目标类型
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 指标目标对象主键
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 月份季度或年度周期编码
     */
    @TableField("period_key")
    private String periodKey;

    /**
     * 该周期设定目标值
     */
    @TableField("target_value")
    private BigDecimal targetValue;

    /**
     * 最近一次已计算实际值
     */
    @TableField("actual_value")
    private BigDecimal actualValue;

    /**
     * 实际值最近计算时间
     */
    @TableField("calculated_time")
    private LocalDateTime calculatedTime;

    /**
     * 计算口径来源和结果解释快照
     */
    @TableField("calculation_json")
    private String calculationJson;

    /**
     * 目标启用或停用状态
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

    public Long getKpiId() {
        return kpiId;
    }

    public void setKpiId(Long kpiId) {
        this.kpiId = kpiId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
    }

    public LocalDateTime getCalculatedTime() {
        return calculatedTime;
    }

    public void setCalculatedTime(LocalDateTime calculatedTime) {
        this.calculatedTime = calculatedTime;
    }

    public String getCalculationJson() {
        return calculationJson;
    }

    public void setCalculationJson(String calculationJson) {
        this.calculationJson = calculationJson;
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
