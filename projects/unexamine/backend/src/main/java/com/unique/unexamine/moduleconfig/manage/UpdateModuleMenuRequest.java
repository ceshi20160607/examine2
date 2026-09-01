package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateModuleMenuRequest(
        Long parentId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String icon,
        @NotBlank @Size(max = 255) String routePath,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull Boolean visible,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotNull @Min(0) Integer version) {
}
