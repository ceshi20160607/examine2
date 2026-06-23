package com.unique.examine.plat.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台角色返回对象。
 */
@Data
@Builder
@Schema(description = "平台角色返回对象")
public class PlatformRoleVO {

    @Schema(description = "角色 ID")
    private String roleId;

    @Schema(description = "角色编码")
    private String code;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否保护角色")
    private Boolean protectedFlag;

    @Schema(description = "平台菜单 ID 集合")
    private List<String> menuIds;

    @Schema(description = "平台操作权限编码集合")
    private List<String> operationCodes;
}
