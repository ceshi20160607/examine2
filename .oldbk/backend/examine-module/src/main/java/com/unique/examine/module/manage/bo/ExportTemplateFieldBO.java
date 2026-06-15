package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 导出模板字段入参。
 */
@Data
@Schema(description = "导出模板字段入参")
public class ExportTemplateFieldBO {

    @Schema(description = "字段 ID")
    @NotNull(message = "字段 ID 不能为空")
    private Long fieldId;

    @Schema(description = "导出表头")
    private String headerName;

    @Schema(description = "列顺序")
    private Integer columnOrder;

    @Schema(description = "是否要求明文导出权限")
    private Byte plainRequiredFlag;

    @Schema(description = "脱敏策略编码")
    private String maskStrategy;

    @Schema(description = "格式化配置 JSON")
    private String formatJson;
}
