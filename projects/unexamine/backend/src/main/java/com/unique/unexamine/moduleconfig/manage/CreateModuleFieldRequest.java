package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleFieldRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String fieldType,
        @NotNull Boolean required,
        Boolean uniqueValue,
        Boolean searchable,
        Long dictionaryId,
        Long referenceModuleId,
        @Min(0) Integer sortOrder,
        @NotNull JsonNode config) {
}
