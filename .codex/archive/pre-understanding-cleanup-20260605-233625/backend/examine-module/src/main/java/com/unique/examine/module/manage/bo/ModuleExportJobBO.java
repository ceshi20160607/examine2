package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模块导出任务入参。
 */
@Data
@Schema(description = "模块导出任务入参")
public class ModuleExportJobBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "导出请求 JSON")
    private String requestJson;
}
