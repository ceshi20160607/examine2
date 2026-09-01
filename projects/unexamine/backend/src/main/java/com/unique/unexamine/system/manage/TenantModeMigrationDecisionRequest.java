package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TenantModeMigrationDecisionRequest(
        boolean approved,
        @Size(max = 1000) String comment,
        @NotNull Integer expectedVersion) {
}
