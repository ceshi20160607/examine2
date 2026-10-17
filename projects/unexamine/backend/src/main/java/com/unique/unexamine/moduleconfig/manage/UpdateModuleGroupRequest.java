package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 修改模块组名称和导航顺序；版本号用于拒绝覆盖其他管理员的修改。 */
public record UpdateModuleGroupRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull @Min(0) Integer version) {
}
