package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SsoCompleteRequest(
        @NotBlank @Size(max = 2000) String code,
        @NotBlank @Size(max = 200) String state) {
}
