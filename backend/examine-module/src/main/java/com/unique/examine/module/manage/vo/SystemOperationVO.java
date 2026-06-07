package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统操作权限信息。
 */
@Data
@Builder
@Schema(description = "系统操作权限信息")
public class SystemOperationVO {

    @Schema(description = "操作 ID")
    private String operationId;

    @Schema(description = "所属菜单 ID")
    private String menuId;

    @Schema(description = "操作编码")
    private String code;

    @Schema(description = "操作名称")
    private String name;

    @Schema(description = "操作类型")
    private String operationType;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "API 路径模式")
    private String apiPattern;

    @Schema(description = "HTTP 方法")
    private String method;

    @Schema(description = "状态")
    private String status;
}
