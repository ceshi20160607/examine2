package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String code,
        @NotBlank @Size(max = 200) String name) {
}
