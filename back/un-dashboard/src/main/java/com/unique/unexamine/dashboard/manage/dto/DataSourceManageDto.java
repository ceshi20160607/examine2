package com.unique.unexamine.dashboard.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 数据源配置管理接口模型；数据源只允许引用模块和模块关联配置。 */
public final class DataSourceManageDto {
    private DataSourceManageDto() {}

    /** 保存数据源草稿。 */
    public record SaveRequest(
            @Schema(description = "已有数据源主键；新增时为空") Long id,
            @NotBlank @Size(max = 20) @Schema(description = "SYSTEM 或 TENANT") String scopeType,
            @NotBlank @Size(max = 80) @Schema(description = "作用域内稳定编码") String code,
            @NotBlank @Size(max = 120) @Schema(description = "数据源名称") String name,
            @Schema(description = "起始模块主键") Long rootModuleId,
            @Size(max = 500) @Schema(description = "数据源业务口径说明") String description) {}

    /** 保存模块关联、输出字段、筛选和排序草稿。 */
    public record SaveConfigRequest(
            @Schema(description = "模块关联路径和连接方式") JsonNode relation,
            @Schema(description = "允许输出的模块字段主键或稳定标识") JsonNode output,
            @Schema(description = "固定字段筛选条件") JsonNode filter,
            @Schema(description = "默认排序和时间字段") JsonNode sort) {}

    /** 数据源详情和当前配置。 */
    public record DataSourceView(Long id, String scopeType, Long systemId, Long tenantId,
                                 String code, String name, Long rootModuleId, String description,
                                 String status, Integer publishedVersion, LocalDateTime updateTime,
                                 ConfigView config) {}

    /** 数据源草稿与发布配置。 */
    public record ConfigView(Long id, Long dataSourceId, JsonNode relation, JsonNode output,
                             JsonNode filter, JsonNode sort, JsonNode published,
                             Integer publishedVersion) {}
}
