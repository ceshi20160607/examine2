package com.unique.examine.app.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI 可授权 scope 目录。
 */
@Data
@Builder
@Schema(description = "OpenAPI 可授权 scope 目录")
public class OpenApiScopeCatalogVO {

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "可授权 scope")
    private List<String> scopeCodes;

    @Schema(description = "说明")
    private String description;
}
