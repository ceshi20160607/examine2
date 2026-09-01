package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAccessRequest(
        Long tenantId,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 100) String requestedRole) {
}
