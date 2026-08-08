package com.unique.examine.plat.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,31}$") String username,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotBlank @Size(max = 160) String systemName,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,31}$") String systemCode
) {
}
