package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 导出模板字段展示对象。
 */
@Data
@Builder
@Schema(description = "导出模板字段展示对象")
public class ExportTemplateFieldVO {

    @Schema(description = "模板字段 ID")
    private String templateFieldId;

    @Schema(description = "字段 ID")
    private String fieldId;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "导出表头")
    private String headerName;

    @Schema(description = "列顺序")
    private Integer columnOrder;

    @Schema(description = "是否要求明文导出权限")
    private Boolean plainRequired;

    @Schema(description = "脱敏策略编码")
    private String maskStrategy;
}
