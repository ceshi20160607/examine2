package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台配置更新入参。
 */
@Data
@Schema(description = "平台配置更新入参")
public class PlatformConfigUpdateBO {

    @Schema(description = "配置值，敏感配置不允许用脱敏占位符覆盖")
    @NotNull(message = "配置值不能为空")
    private String value;

    @Schema(description = "备注")
    private String remark;
}
