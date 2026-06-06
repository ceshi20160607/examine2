package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流程模板保存入参")
public class WorkflowTemplateSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long moduleId;
    @NotBlank private String templateName;
    @Schema(description = "状态：DRAFT/PUBLISHED/DISABLED") private String status;
}

