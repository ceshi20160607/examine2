package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 仪表组件对应的字段以及相关配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_dashboard_component_field")
public class UnDashboardComponentField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 仪表盘组件字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属组件主键
     */
    @TableField("component_id")
    private Long componentId;

    /**
     * 数据源中的字段编码
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * 组件显示字段名称
     */
    @TableField("field_label")
    private String fieldLabel;

    /**
     * 统计聚合方式
     */
    @TableField("aggregation")
    private String aggregation;

    /**
     * 字段显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
