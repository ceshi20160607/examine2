package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateTenantShareRequest(
        @NotNull Long targetTenantId,
        List<String> allowedActions,
        LocalDateTime expiresAt,
        Integer version) {
}
