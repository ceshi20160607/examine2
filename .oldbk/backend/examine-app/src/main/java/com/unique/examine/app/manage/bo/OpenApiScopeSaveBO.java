package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OpenAPI scope 授权入参。
 */
@Data
@Schema(description = "OpenAPI scope 授权入参")
public class OpenApiScopeSaveBO {

    @NotBlank(message = "scopeCode 不能为空")
    @Schema(description = "scope 编码，如 record:read、record:create")
    private String scopeCode;

    @Schema(description = "限定模块 ID")
    private Long moduleId;

    @Schema(description = "字段读写权限 JSON")
    private String fieldPermissionJson;

    @Schema(description = "数据范围规则 JSON")
    private String dataScopeJson;

    @Schema(description = "ENABLED、DISABLED")
    private String status;
}
