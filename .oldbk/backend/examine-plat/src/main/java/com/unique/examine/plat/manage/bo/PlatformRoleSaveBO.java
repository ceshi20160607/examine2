package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存平台角色入参。
 */
@Data
@Schema(description = "保存平台角色入参")
public class PlatformRoleSaveBO {

    @Schema(description = "角色编码，全局唯一")
    @NotBlank(message = "角色编码不能为空")
    private String code;

    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String name;

    @Schema(description = "角色说明")
    private String description;
}
