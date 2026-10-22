package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 仪表组件字段配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_dashboard_component_field")
public class UnDashboardComponentField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 仪表组件字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表组件主键
     */
    @TableField("component_id")
    private Long componentId;

    /**
     * 数据源输出字段或模块字段稳定主键
     */
    @TableField("source_field_id")
    private Long sourceFieldId;

    /**
     * 指标分组时间或排序字段用途
     */
    @TableField("field_role")
    private String fieldRole;

    /**
     * 计数求和平均最大或最小计算方式
     */
    @TableField("aggregation")
    private String aggregation;

    /**
     * 组件内字段顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 展示格式及字段级补充配置
     */
    @TableField("config_json")
    private String configJson;

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

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    public String getFieldRole() {
        return fieldRole;
    }

    public void setFieldRole(String fieldRole) {
        this.fieldRole = fieldRole;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
