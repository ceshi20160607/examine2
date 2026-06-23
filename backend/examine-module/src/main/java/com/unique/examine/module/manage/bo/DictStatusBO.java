package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典状态变更入参。
 */
@Data
@Schema(description = "字典状态变更入参")
public class DictStatusBO {

    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态：ENABLED、DISABLED")
    private String targetStatus;

    @Schema(description = "变更原因")
    private String reason;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "版本号，使用 cacheVersion")
    private Long version;
}
