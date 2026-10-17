package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 可视化仪表盘组件草稿与固定数据源版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard_component")
public class AnaDashboardComponent {

    /**
     * 仪表盘组件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表盘主键
     */
    @TableField("dashboard_id")
    private Long dashboardId;

    /**
     * 组件归属层级，与仪表盘及数据源版本一致
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 组件归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 仪表盘内稳定的组件编码
     */
    @TableField("component_key")
    private String componentKey;

    /**
     * 统计卡、图表、列表、待办、KPI 或快捷入口类型
     */
    @TableField("component_type")
    private String componentType;

    /**
     * 组件显示标题
     */
    @TableField("title")
    private String title;

    /**
     * 组件使用的数据源主键，无查询来源的组件可为空
     */
    @TableField("data_source_id")
    private Long dataSourceId;

    /**
     * 草稿明确绑定的已发布数据源版本主键
     */
    @TableField("data_source_version_id")
    private Long dataSourceVersionId;

    /**
     * 被引用数据源版本的归属层级，允许同系统默认或主租户共享来源
     */
    @TableField("source_scope_type")
    private String sourceScopeType;

    /**
     * 被引用数据源版本的归属主体主键
     */
    @TableField("source_scope_owner_id")
    private Long sourceScopeOwnerId;

    /**
     * 拖拽坐标和组件尺寸草稿
     */
    @TableField("layout_json")
    private String layoutJson;

    /**
     * 统计字段、过滤条件和时间范围草稿
     */
    @TableField("query_parameter_json")
    private String queryParameterJson;

    /**
     * 图表和展示样式草稿
     */
    @TableField("display_config_json")
    private String displayConfigJson;

    /**
     * 点击后跳转的模块列表或详情配置
     */
    @TableField("drill_target_json")
    private String drillTargetJson;

    /**
     * 同一仪表盘内的排序序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

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
     * 乐观锁版本号
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

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
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
        return "AnaDashboardComponent{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", componentKey = " + componentKey +
            ", componentType = " + componentType +
            ", title = " + title +
            ", dataSourceId = " + dataSourceId +
            ", dataSourceVersionId = " + dataSourceVersionId +
            ", sourceScopeType = " + sourceScopeType +
            ", sourceScopeOwnerId = " + sourceScopeOwnerId +
            ", layoutJson = " + layoutJson +
            ", queryParameterJson = " + queryParameterJson +
            ", displayConfigJson = " + displayConfigJson +
            ", drillTargetJson = " + drillTargetJson +
            ", sortOrder = " + sortOrder +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
