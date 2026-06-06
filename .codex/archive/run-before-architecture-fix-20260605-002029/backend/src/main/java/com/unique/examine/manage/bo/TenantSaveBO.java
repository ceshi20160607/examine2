package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "租户保存入参")
public class TenantSaveBO {
    @NotBlank
    @Schema(description = "租户名称")
    private String tenantName;
    @NotBlank
    @Schema(description = "租户编码")
    private String tenantCode;
    @Schema(description = "负责人账号ID")
    private Long ownerAccountId;
    @Schema(description = "状态：ENABLED/DISABLED")
    private String status;
}

