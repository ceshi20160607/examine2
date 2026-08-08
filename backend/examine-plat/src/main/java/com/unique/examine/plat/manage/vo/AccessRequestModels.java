package com.unique.examine.plat.manage.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AccessRequestModels {
    private AccessRequestModels() {
    }

    public record Submit(
            String targetTenantId,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record Cancel(@NotBlank String version) {
    }

    public record View(
            String id,
            String systemId,
            String systemName,
            String accountId,
            String accountName,
            String targetTenantId,
            String targetTenantName,
            String reason,
            String status,
            String reviewReason,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            String version
    ) {
    }
}
