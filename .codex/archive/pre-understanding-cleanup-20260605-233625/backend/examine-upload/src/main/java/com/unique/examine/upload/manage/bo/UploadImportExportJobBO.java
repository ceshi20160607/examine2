package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 导入导出任务创建入参。
 */
@Data
@Schema(description = "导入导出任务创建入参")
public class UploadImportExportJobBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "任务类型：IMPORT、EXPORT")
    private String jobType;

    @Schema(description = "导入源文件 ID")
    private Long sourceFileId;

    @Schema(description = "请求参数 JSON")
    private String requestJson;
}
