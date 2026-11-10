package com.unique.unexamine.dashboard.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** KPI 配置、目标和运行结果接口模型。 */
public final class KpiManageDto {
    private KpiManageDto() {}

    /** 保存 KPI 草稿；模块和数据源只能选择一个。 */
    public record SaveRequest(
            @Schema(description = "已有 KPI 主键；新增时为空") Long id,
            @NotBlank @Size(max = 80) @Schema(description = "租户内稳定编码") String code,
            @NotBlank @Size(max = 120) @Schema(description = "指标名称") String name,
            @Schema(description = "已发布数据源主键") Long dataSourceId,
            @Schema(description = "直接计算的模块主键") Long moduleId,
            @Schema(description = "求和、平均、最大或最小使用的字段主键") Long measureFieldId,
            @NotBlank @Size(max = 30) @Schema(description = "COUNT、SUM、AVG、MAX 或 MIN") String aggregation,
            @NotBlank @Size(max = 20) @Schema(description = "MONTH、QUARTER 或 YEAR") String periodType,
            @Schema(description = "筛选和预警配置；periodFieldId 可指定日期字段，未指定时按记录创建时间归期") JsonNode config) {}

    /** 保存一个周期目标。 */
    public record TargetRequest(
            @Schema(description = "已有目标主键；新增时为空") Long id,
            @NotBlank @Size(max = 20) @Schema(description = "ACCOUNT、MEMBER、TENANT_MEMBER、DEPARTMENT 或 ROLE") String targetType,
            @NotNull @Schema(description = "目标对象主键") Long targetId,
            @NotBlank @Size(max = 20) @Schema(description = "月份、季度或年度编码") String periodKey,
            @NotNull @Schema(description = "目标值") BigDecimal targetValue,
            @Schema(description = "目标是否启用") Boolean active) {}

    /** KPI 配置及其当前发布信息。 */
    public record KpiView(Long id, Long systemId, Long tenantId, String code, String name,
                          Long dataSourceId, Long moduleId, Long measureFieldId, String aggregation,
                          String periodType, String status, Integer publishedVersion,
                          JsonNode config, JsonNode publishedConfig, LocalDateTime updateTime) {}

    /** 周期目标及最近计算值。 */
    public record TargetView(Long id, Long kpiId, String targetType, Long targetId,
                             String periodKey, BigDecimal targetValue, BigDecimal actualValue,
                             LocalDateTime calculatedTime, String status,
                             @Schema(description = "实际值除以目标值的百分比；目标为零时为空") BigDecimal completionRate,
                             @Schema(description = "ACHIEVED、BELOW_TARGET 或 NO_TARGET") String achievementStatus) {}

    /** KPI 运行计算结果；sourceJson 用于解释实际统计口径。 */
    public record RuntimeView(Long kpiId, String code, String name, Integer publishedVersion,
                              String periodKey, BigDecimal actualValue, LocalDateTime calculatedTime,
                              JsonNode source, java.util.List<TargetView> targets) {}

    /** 已完成的周期计算快照；目标主键为空表示全租户实际值。 */
    public record ResultView(
            @Schema(description = "计算历史主键") Long id,
            @Schema(description = "所属 KPI 主键") Long kpiId,
            @Schema(description = "月份、季度或年度周期编码") String periodKey,
            @Schema(description = "目标主键；为空表示全租户结果") Long kpiUserId,
            @Schema(description = "计算使用的已发布 KPI 版本") Integer kpiVersion,
            @Schema(description = "本次实际值") BigDecimal actualValue,
            @Schema(description = "来源、筛选、目标值和匹配记录数快照") JsonNode source,
            @Schema(description = "计算时间") LocalDateTime calculationTime) {}
}
