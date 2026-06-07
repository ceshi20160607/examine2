package com.unique.examine.app.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI scope 授权返回。
 */
@Data
@Builder
@Schema(description = "OpenAPI scope 授权返回")
public class OpenApiScopeVO {

    @Schema(description = "scope ID")
    private Long scopeId;

    @Schema(description = "scope 编码")
    private String scopeCode;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "字段权限 JSON")
    private String fieldPermissionJson;

    @Schema(description = "数据范围 JSON")
    private String dataScopeJson;

    @Schema(description = "状态")
    private String status;
}
