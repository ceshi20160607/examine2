package com.unique.unexamine.analytics.base.entity;

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
@TableName("ana_dashboard_component")
public class AnaDashboardComponent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("dashboard_id")
    private Long dashboardId;

    @TableField("component_key")
    private String componentKey;

    @TableField("component_type")
    private String componentType;

    @TableField("title")
    private String title;

    @TableField("data_source_id")
    private Long dataSourceId;

    @TableField("layout_json")
    private String layoutJson;

    @TableField("query_parameter_json")
    private String queryParameterJson;

    @TableField("display_config_json")
    private String displayConfigJson;

    @TableField("drill_target_json")
    private String drillTargetJson;

    @TableField("sort_order")
    private Integer sortOrder;

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

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public String getQueryParameterJson() {
        return queryParameterJson;
    }

    public void setQueryParameterJson(String queryParameterJson) {
        this.queryParameterJson = queryParameterJson;
    }

    public String getDisplayConfigJson() {
        return displayConfigJson;
    }

    public void setDisplayConfigJson(String displayConfigJson) {
        this.displayConfigJson = displayConfigJson;
    }

    public String getDrillTargetJson() {
        return drillTargetJson;
    }

    public void setDrillTargetJson(String drillTargetJson) {
        this.drillTargetJson = drillTargetJson;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        return "AnaDashboardComponent{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", componentKey = " + componentKey +
            ", componentType = " + componentType +
            ", title = " + title +
            ", dataSourceId = " + dataSourceId +
            ", layoutJson = " + layoutJson +
            ", queryParameterJson = " + queryParameterJson +
            ", displayConfigJson = " + displayConfigJson +
            ", drillTargetJson = " + drillTargetJson +
            ", sortOrder = " + sortOrder +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
