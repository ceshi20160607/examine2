package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "模块保存入参")
public class ModuleSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long appId;
    @NotBlank private String moduleName;
    @NotBlank private String moduleCode;
    @Schema(description = "模块类型：NORMAL/SUB_TABLE") private String moduleType;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

