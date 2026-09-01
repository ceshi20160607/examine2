package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountProfileUpdateRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 255) String email,
        @Size(max = 40) String mobile,
        @NotBlank @Size(max = 32) String locale,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Integer version) {
}
