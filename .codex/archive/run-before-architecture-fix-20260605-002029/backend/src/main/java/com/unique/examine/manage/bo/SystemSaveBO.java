package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "业务系统保存入参")
public class SystemSaveBO {
    @NotNull
    @Schema(description = "租户ID")
    private Long tenantId;
    @NotBlank
    @Schema(description = "系统名称")
    private String systemName;
    @NotBlank
    @Schema(description = "系统编码")
    private String systemCode;
    @Schema(description = "系统拥有者账号ID，空时取当前账号")
    private Long ownerAccountId;
    @Schema(description = "系统描述")
    private String description;
    @Schema(description = "状态：DRAFT/ENABLED/DISABLED")
    private String status;
}

