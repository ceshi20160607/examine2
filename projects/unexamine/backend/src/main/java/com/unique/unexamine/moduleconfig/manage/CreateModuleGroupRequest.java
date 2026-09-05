package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleGroupRequest(
        @Size(max = 50) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String code,
        @jakarta.validation.constraints.NotBlank @Size(max = 200) String name,
        @Min(0) Integer sortOrder) {
}
