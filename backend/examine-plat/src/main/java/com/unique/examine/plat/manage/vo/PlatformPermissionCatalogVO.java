package com.unique.examine.plat.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台权限目录返回对象。
 */
@Data
@Builder
@Schema(description = "平台权限目录返回对象")
public class PlatformPermissionCatalogVO {

    @Schema(description = "平台菜单树")
    private List<PlatformMenuTreeVO> menus;

    @Schema(description = "平台操作权限编码集合")
    private List<String> operationCodes;
}
