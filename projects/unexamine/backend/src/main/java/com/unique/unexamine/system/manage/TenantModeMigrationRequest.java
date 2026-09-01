package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantModeMigrationRequest(
        @NotBlank @Pattern(regexp = "SINGLE|MULTI") String toMode) {
}
