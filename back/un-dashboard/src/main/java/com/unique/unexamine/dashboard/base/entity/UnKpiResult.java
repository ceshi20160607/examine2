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
 * 可解释的指标计算历史
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_kpi_result")
public class UnKpiResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 指标计算历史主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属人员或组织周期目标主键
     */
    @TableField("kpi_user_id")
    private Long kpiUserId;

    /**
     * 计算所用的已发布指标版本
     */
    @TableField("kpi_version")
    private Integer kpiVersion;

    /**
     * 本次计算的实际指标值
     */
    @TableField("actual_value")
    private BigDecimal actualValue;

    /**
     * 来源模块数据源筛选口径与权限摘要
     */
    @TableField("source_json")
    private String sourceJson;

    /**
     * 指标实际计算时间
     */
    @TableField("calculation_time")
    private LocalDateTime calculationTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKpiUserId() {
        return kpiUserId;
    }

    public void setKpiUserId(Long kpiUserId) {
        this.kpiUserId = kpiUserId;
    }

    public Integer getKpiVersion() {
        return kpiVersion;
    }

    public void setKpiVersion(Integer kpiVersion) {
        this.kpiVersion = kpiVersion;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
    }

    public String getSourceJson() {
        return sourceJson;
    }

    public void setSourceJson(String sourceJson) {
        this.sourceJson = sourceJson;
    }

    public LocalDateTime getCalculationTime() {
        return calculationTime;
    }

    public void setCalculationTime(LocalDateTime calculationTime) {
        this.calculationTime = calculationTime;
    }
}
