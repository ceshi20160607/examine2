package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "应用保存入参")
public class AppSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotBlank private String appName;
    @NotBlank private String appCode;
    @Schema(description = "状态：DRAFT/ENABLED/DISABLED") private String status;
    @Schema(description = "排序号") private Integer sortOrder;
}

