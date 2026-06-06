package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenAPI授权范围保存入参")
public class OpenApiScopeSaveBO {
    @NotNull private Long clientPk;
    @NotBlank private String scopeType;
    @NotBlank private String scopeValue;
}

