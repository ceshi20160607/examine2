package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleFieldRequest(
        @Size(max = 50) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String fieldType,
        @NotNull Boolean required,
        Boolean uniqueValue,
        Boolean searchable,
        Long dictionaryId,
        Long referenceModuleId,
        @Min(0) Integer sortOrder,
        @NotNull JsonNode config,
        @Min(1) Long parentFieldId) {

    /** 保留既有主记录字段创建调用；子表列需要显式提供所属子表字段。 */
    public CreateModuleFieldRequest(String code, String name, String fieldType, Boolean required,
                                    Boolean uniqueValue, Boolean searchable, Long dictionaryId,
                                    Long referenceModuleId, Integer sortOrder, JsonNode config) {
        this(code, name, fieldType, required, uniqueValue, searchable, dictionaryId,
                referenceModuleId, sortOrder, config, null);
    }
}
