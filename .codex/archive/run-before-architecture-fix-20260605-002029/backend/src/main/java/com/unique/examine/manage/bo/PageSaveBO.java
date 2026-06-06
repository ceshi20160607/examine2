package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "页面配置保存入参")
public class PageSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long moduleId;
    @NotBlank private String pageType;
    @Schema(description = "应用版本ID") private Long appVersionId;
    @NotBlank private String layoutJson;
    @Schema(description = "页面块配置JSON") private String blockJson;
    @Schema(description = "状态：DRAFT/PUBLISHED/DISABLED") private String status;
}

