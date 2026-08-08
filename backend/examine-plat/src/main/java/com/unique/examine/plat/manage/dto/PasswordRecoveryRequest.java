package com.unique.examine.plat.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordRecoveryRequest(
        @NotBlank @Size(max = 254) String account
) {
}
