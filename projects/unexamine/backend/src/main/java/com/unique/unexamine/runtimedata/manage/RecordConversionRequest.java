package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RecordConversionRequest(
        @NotNull Integer version,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String targetModuleCode) {
}
