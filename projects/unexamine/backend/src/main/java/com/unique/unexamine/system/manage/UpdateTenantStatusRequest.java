package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTenantStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
}
