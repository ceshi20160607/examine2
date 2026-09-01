package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordLifecycleRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotNull Integer version) {
}
