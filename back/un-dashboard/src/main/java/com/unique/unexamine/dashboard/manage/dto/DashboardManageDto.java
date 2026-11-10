package com.unique.unexamine.dashboard.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 仪表盘配置管理接口模型；草稿和已发布快照分开返回。 */
public final class DashboardManageDto {
    private DashboardManageDto() {}

    /** 保存仪表盘草稿；scopeType 为 PERSONAL 时只能由当前账号维护。 */
    public record SaveRequest(
            @Schema(description = "已有仪表盘主键；新增时为空") Long id,
            @NotBlank @Size(max = 20) @Schema(description = "SYSTEM、TENANT、MODULE 或 PERSONAL") String scopeType,
            @Schema(description = "模块首页仪表盘的模块主键") Long moduleId,
            @NotBlank @Size(max = 80) @Schema(description = "作用域内稳定编码") String code,
            @NotBlank @Size(max = 120) @Schema(description = "仪表盘名称") String name,
            @Schema(description = "设计器布局对象") JsonNode layout) {}

    /** 保存一个组件草稿；moduleId 和 dataSourceId 最多配置一个。 */
    public record SaveComponentRequest(
            @Schema(description = "已有组件主键；新增时为空") Long id,
            @NotBlank @Size(max = 80) @Schema(description = "仪表盘内稳定组件编码") String componentCode,
            @NotBlank @Size(max = 40) @Schema(description = "列表、图表、指标、待办或快捷入口") String componentType,
            @Schema(description = "已发布数据源主键") Long dataSourceId,
            @Schema(description = "直接查询的模块主键") Long moduleId,
            @Schema(description = "组件筛选、展示和跳转配置") JsonNode config,
            @Schema(description = "组件排序") Integer sortNo) {}

    /** 保存仪表盘访问范围；subjectType 支持账号、系统成员、租户成员、部门和角色。 */
    public record AccessRequest(
            @NotNull @Schema(description = "访问主体列表") List<AccessSubject> subjects) {}

    /** 一个访问主体只能在同一仪表盘中配置一次。 */
    public record AccessSubject(
            @NotBlank @Size(max = 30) @Schema(description = "ACCOUNT、MEMBER、TENANT_MEMBER、DEPARTMENT 或 ROLE") String subjectType,
            @NotNull @Schema(description = "访问主体主键") Long subjectId) {}

    /** 发布后运行态使用的仪表盘详情。 */
    public record DashboardView(Long id, String scopeType, Long systemId, Long tenantId,
                                Long moduleId, Long ownerAccountId, String code, String name,
                                String status, Integer publishedVersion, JsonNode layout,
                                JsonNode publishedLayout, LocalDateTime updateTime,
                                List<ComponentView> components) {}

    /** 仪表盘草稿和已发布组件配置。 */
    public record ComponentView(Long id, Long dashboardId, String componentCode,
                                String componentType, Long dataSourceId, Long moduleId,
                                String status, Integer sortNo, JsonNode config,
                                JsonNode publishedConfig) {}
}
