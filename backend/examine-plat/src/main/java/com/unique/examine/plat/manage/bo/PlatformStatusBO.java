package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 状态变更入参。
 */
@Data
@Schema(description = "状态变更入参")
public class PlatformStatusBO {

    @Schema(description = "目标状态")
    @NotBlank(message = "状态不能为空")
    private String status;

    @Schema(description = "变更原因")
    private String reason;
}
