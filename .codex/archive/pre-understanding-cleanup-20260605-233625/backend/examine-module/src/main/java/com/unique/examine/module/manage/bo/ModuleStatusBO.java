package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模块状态变更入参。
 */
@Data
@Schema(description = "模块状态变更入参")
public class ModuleStatusBO {

    @Schema(description = "状态：DRAFT、PUBLISHED、DISABLED")
    private String status;
}
