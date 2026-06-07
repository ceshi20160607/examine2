package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统基础信息更新入参。
 */
@Data
@Schema(description = "系统基础信息更新入参")
public class SystemProfileUpdateBO {

    @NotBlank(message = "系统名称不能为空")
    @Schema(description = "系统名称")
    private String name;

    @Schema(description = "系统描述")
    private String description;

    @Schema(description = "系统访问域名或入口标识")
    private String domain;
}
