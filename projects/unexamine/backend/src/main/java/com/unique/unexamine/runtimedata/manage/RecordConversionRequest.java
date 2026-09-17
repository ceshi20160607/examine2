package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** 转化使用来源版本和已配置的目标模块；不接受客户端指定系统、租户或字段映射。 */
public record RecordConversionRequest(
        @NotNull @Min(0) Integer version,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String targetModuleCode) {
}
