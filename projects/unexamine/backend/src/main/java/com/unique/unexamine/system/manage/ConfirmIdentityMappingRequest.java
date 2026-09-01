package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ConfirmIdentityMappingRequest(
        @NotBlank String preflightId,
        @AssertTrue boolean confirmed) {
}
