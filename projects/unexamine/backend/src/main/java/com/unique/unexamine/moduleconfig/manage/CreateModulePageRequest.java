package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModulePageRequest(
        @NotBlank @Pattern(regexp = "LIST|FORM|DETAIL|DASHBOARD|CUSTOM") String pageType,
        @NotBlank @Size(max = 200) String name,
        @NotNull JsonNode layout) {
}
