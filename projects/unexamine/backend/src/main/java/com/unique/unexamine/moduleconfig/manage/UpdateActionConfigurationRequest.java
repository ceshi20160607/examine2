package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateActionConfigurationRequest(
        @NotBlank @Pattern(regexp = "MODULE_ENTRY|LIST_TOOLBAR|BATCH|ROW|DETAIL_HEADER|DETAIL_MORE") String location,
        @NotNull JsonNode config,
        @NotNull @Min(0) Integer version) {
}
