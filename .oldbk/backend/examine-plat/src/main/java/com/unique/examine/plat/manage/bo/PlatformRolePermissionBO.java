package com.unique.examine.plat.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台角色权限保存入参。
 */
@Data
@Schema(description = "平台角色权限保存入参")
public class PlatformRolePermissionBO {

    @Schema(description = "平台菜单 ID 集合")
    @NotNull(message = "菜单 ID 集合不能为空")
    private List<Long> menuIds;

    @Schema(description = "平台操作权限编码集合")
    @NotNull(message = "操作权限编码集合不能为空")
    private List<String> operationCodes;
}
