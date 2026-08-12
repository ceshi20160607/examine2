package com.unique.examine.plat.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String account,
        @NotBlank @Size(max = 200) String password
) {
}
