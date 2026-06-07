package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 应用更新入参。
 */
@Data
@Schema(description = "应用更新入参")
public class AppUpdateBO {

    @Schema(description = "应用名称")
    private String name;

    @Schema(description = "应用图标")
    private String icon;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "乐观锁版本")
    private Integer version;
}
