package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 导出模板保存入参。
 */
@Data
@Schema(description = "导出模板保存入参")
public class ExportTemplateSaveBO {

    @Schema(description = "模块 ID")
    @NotNull(message = "模块 ID 不能为空")
    private Long moduleId;

    @Schema(description = "模板编码")
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @Schema(description = "模板名称")
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "结果文件名规则")
    private String fileNamePattern;

    @Schema(description = "导出格式，MVP 默认 CSV")
    private String exportFormat;

    @Schema(description = "是否导出历史")
    private Byte includeHistoryFlag;

    @Schema(description = "表头、冻结列、样式等配置 JSON")
    private String configJson;

    @Schema(description = "导出字段")
    @Valid
    private List<ExportTemplateFieldBO> fields;
}
