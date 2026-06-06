package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "导入导出任务保存入参")
public class ImportExportTaskSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    private Long appId;
    private Long moduleId;
    @NotBlank private String taskType;
    private Long templateId;
}

