package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 租户保存入参。
 */
@Data
@Schema(description = "租户保存入参")
public class TenantSaveBO {

    @NotBlank(message = "租户编码不能为空")
    @Schema(description = "租户编码")
    private String code;

    @NotBlank(message = "租户名称不能为空")
    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户描述")
    private String description;
}
