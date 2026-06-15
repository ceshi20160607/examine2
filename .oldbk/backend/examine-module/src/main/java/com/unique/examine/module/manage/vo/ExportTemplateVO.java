package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 导出模板展示对象。
 */
@Data
@Builder
@Schema(description = "导出模板展示对象")
public class ExportTemplateVO {

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板状态")
    private String templateStatus;

    @Schema(description = "结果文件名规则")
    private String fileNamePattern;

    @Schema(description = "导出格式")
    private String exportFormat;

    @Schema(description = "是否导出历史")
    private Boolean includeHistory;

    @Schema(description = "字段列表")
    private List<ExportTemplateFieldVO> fields;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
