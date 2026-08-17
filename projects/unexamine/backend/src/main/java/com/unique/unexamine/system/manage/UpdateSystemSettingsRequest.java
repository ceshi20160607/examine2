package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingsRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "SINGLE|MULTI") String tenantMode) {
}
