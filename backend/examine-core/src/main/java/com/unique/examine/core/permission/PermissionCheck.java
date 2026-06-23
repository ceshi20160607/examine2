package com.unique.examine.core.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 权限判定入参。
 */
@Data
@Builder
@Schema(description = "权限判定入参")
public class PermissionCheck {

    @Schema(description = "菜单编码")
    private String menuCode;

    @Schema(description = "操作编码")
    private String operationCode;

    @Schema(description = "OpenAPI scope 编码")
    private String openApiScope;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段动作：VISIBLE、WRITABLE、EXPORT_PLAIN、OPENAPI_READ、OPENAPI_WRITE")
    private String fieldAction;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "资源 ID")
    private String resourceId;

    @Schema(description = "目标成员 ID，用于数据范围判定")
    private String targetMemberId;
}
