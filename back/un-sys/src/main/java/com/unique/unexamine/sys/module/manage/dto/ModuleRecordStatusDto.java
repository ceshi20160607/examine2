package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 模块业务状态配置；系统预置状态编码不能改名或停用。 */
public final class ModuleRecordStatusDto {
    private ModuleRecordStatusDto() {}

    /** 管理员保存一个模块自定义状态。 */
    public record SaveRequest(@NotBlank @Schema(description="模块内稳定状态编码") String code,
                              @NotBlank @Schema(description="状态展示名称") String name,
                              @Schema(description="是否允许动作使用") boolean enabled) {}

    /** 页面配置动作时可选的状态。 */
    public record View(@Schema(description="稳定状态编码") String code,
                       @Schema(description="展示名称") String name,
                       @Schema(description="是否系统预置") boolean builtin,
                       @Schema(description="是否允许动作使用") boolean enabled) {}
}
