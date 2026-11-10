package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 模块页面审批状态组由管理员命名，再由各审批绑定选择所属组。 */
public final class ModulePageStatusDto {
    private ModulePageStatusDto() {}

    public record SaveRequest(
            @Schema(description="已有状态组主键；新增时为空") Long id,
            @NotBlank @Size(max=80) @Schema(description="模块内稳定状态字段编码") String code,
            @NotBlank @Size(max=100) @Schema(description="状态字段展示名称") String name,
            @Min(0) @Schema(description="页面展示顺序") int sortNo,
            @Schema(description="是否在页面展示") boolean enabled) {}

    public record View(@Schema(description="状态组主键") Long id,
                       @Schema(description="业务模块主键") Long moduleId,
                       @Schema(description="稳定状态字段编码") String code,
                       @Schema(description="展示名称") String name,
                       @Schema(description="展示顺序") int sortNo,
                       @Schema(description="是否在页面展示") boolean enabled) {}
}
