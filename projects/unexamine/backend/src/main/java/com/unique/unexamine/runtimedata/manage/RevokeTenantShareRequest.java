package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RevokeTenantShareRequest(@NotNull Integer version, @NotBlank String reason) {
}
