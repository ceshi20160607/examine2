package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 关键绩效指标配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_kpi")
public class UnKpi implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关键指标主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 指标所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 租户内指标编码
     */
    @TableField("code")
    private String code;

    /**
     * 指标名称
     */
    @TableField("name")
    private String name;

    /**
     * 计算指标的已发布数据源主键
     */
    @TableField("data_source_id")
    private Long dataSourceId;

    /**
     * 直接计算指标的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 参与指标计算的模块字段主键
     */
    @TableField("measure_field_id")
    private Long measureFieldId;

    /**
     * 指标数量求和比率等统计口径
     */
    @TableField("aggregation")
    private String aggregation;

    /**
     * 按月季度或年份考核
     */
    @TableField("period_type")
    private String periodType;

    /**
     * 草稿筛选预警和计算规则
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 已发布指标计算规则快照
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布指标版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getMeasureFieldId() {
        return measureFieldId;
    }

    public void setMeasureFieldId(Long measureFieldId) {
        this.measureFieldId = measureFieldId;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
