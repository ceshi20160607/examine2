package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 复制当前模块草稿到指定模块组；源版本用于避免复制过期配置。 */
public record CopyModuleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long groupId,
        @NotNull @Min(0) Integer version) {
}
