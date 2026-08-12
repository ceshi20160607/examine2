package com.unique.examine.plat.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordRecoveryResetRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43}$") String token,
        @NotBlank @Size(min = 10, max = 200) String newPassword
) {
}
