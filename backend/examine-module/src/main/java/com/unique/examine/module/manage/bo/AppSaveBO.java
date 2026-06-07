package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 应用保存入参。
 */
@Data
@Schema(description = "应用保存入参")
public class AppSaveBO {

    @NotBlank(message = "应用名称不能为空")
    @Schema(description = "应用名称")
    private String name;

    @NotBlank(message = "应用编码不能为空")
    @Schema(description = "应用编码，同系统同租户唯一")
    private String code;

    @Schema(description = "应用图标")
    private String icon;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "租户 ID，空时使用当前请求租户")
    private String tenantId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "应用状态：DRAFT、ENABLED、DISABLED、ARCHIVED")
    private String status;
}
