package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统权限目录。
 */
@Data
@Builder
@Schema(description = "系统权限目录")
public class PermissionCatalogVO {

    @Schema(description = "系统菜单树")
    private List<SystemMenuTreeVO> menus;

    @Schema(description = "操作权限集合")
    private List<SystemOperationVO> operations;

    @Schema(description = "模块字段权限目录")
    private List<String> fieldCodes;

    @Schema(description = "OpenAPI scope 编码集合")
    private List<String> openapiScopes;

    @Schema(description = "数据范围类型")
    private List<String> dataScopeTypes;
}
