package com.unique.unexamine.platform.manage.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String systemName,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{2,49}") String systemCode) {
}
