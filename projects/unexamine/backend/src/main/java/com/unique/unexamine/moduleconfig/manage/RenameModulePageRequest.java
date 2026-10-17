package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 页面编码由类型固定，修改时只接受显示名称与草稿版本。 */
public record RenameModulePageRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Min(0) Integer version) {
}
