package com.unique.unexamine.foundation.manage.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveContextSettingRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,99}") String category,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{1,149}") String settingKey,
        @NotNull Object value,
        boolean sensitive,
        Integer expectedVersion) {
}
