package com.unique.unexamine.dashboard.manage.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** 仪表盘运行态返回模型；只返回当前账号有权读取的已发布配置与数据。 */
public final class DashboardRuntimeDto {
    private DashboardRuntimeDto() {}

    /** 已发布仪表盘及其运行组件。 */
    public record DashboardView(
            @Schema(description = "仪表盘主键") Long id,
            @Schema(description = "仪表盘编码") String code,
            @Schema(description = "仪表盘名称") String name,
            @Schema(description = "作用域") String scopeType,
            @Schema(description = "已发布版本") Integer publishedVersion,
            @Schema(description = "发布时保存的布局") Map<String, Object> layout,
            @Schema(description = "已发布组件") List<ComponentView> components) {}

    /** 单个组件及其已授权的模块数据。 */
    public record ComponentView(
            @Schema(description = "组件主键") Long id,
            @Schema(description = "组件编码") String code,
            @Schema(description = "组件类型") String type,
            @Schema(description = "显示顺序") Integer sortNo,
            @Schema(description = "数据源主键") Long dataSourceId,
            @Schema(description = "直接查询的模块主键") Long moduleId,
            @Schema(description = "已发布组件配置") Map<String, Object> config,
            @Schema(description = "运行结果状态") String dataStatus,
            @Schema(description = "当前账号可读取的模块数据") ModuleDataView data) {}

    /** 模块列表组件复用统一 MyBatis-Plus 分页结构。 */
    public record ModuleDataView(List<ColumnView> columns, Page<RowView> page) {}

    /** 仪表盘组件中的模块列。 */
    public record ColumnView(Long fieldId, String code, String name, int type,
                             Integer widthPx, String fixedSide) {}

    /** 仪表盘组件中的模块行；值已经经过字段权限过滤。 */
    public record RowView(Long id, String name, String status, Long ownerAccountId,
                          Long sourceTenantId, boolean shared,
                          Map<String, Object> values, Map<String, Object> valueDisplay) {}
}
