package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAPI scope 保存入参。
 */
@Data
@Schema(description = "OpenAPI scope 保存入参")
public class OpenApiScopeSaveBO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "授权应用 ID")
    private Long appId;

    @Schema(description = "授权模块 ID")
    private Long moduleId;

    @Schema(description = "scope 编码")
    private String scopeCode;

    @Schema(description = "动作集合：READ、WRITE、DELETE、FLOW")
    private String actions;
}
