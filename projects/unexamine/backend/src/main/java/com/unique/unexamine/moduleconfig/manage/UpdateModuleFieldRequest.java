package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateModuleFieldRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Boolean required,
        @Min(0) Integer sortOrder,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotNull JsonNode config,
        @NotNull @Min(0) Integer version) {
}
