package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建自定义系统入参。
 */
@Data
@Schema(description = "创建自定义系统入参")
public class PlatformSystemSaveBO {

    @Schema(description = "系统名称")
    @NotBlank(message = "系统名称不能为空")
    private String name;

    @Schema(description = "系统编码，全局唯一")
    @NotBlank(message = "系统编码不能为空")
    private String code;

    @Schema(description = "租户模式：SINGLE、MULTI")
    @NotBlank(message = "租户模式不能为空")
    private String tenantMode;

    @Schema(description = "系统描述")
    private String description;
}
