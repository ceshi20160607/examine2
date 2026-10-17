package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 只修改模块显示名称；稳定编码不接受修改。 */
public record RenameModuleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Min(0) Integer version) {
}
