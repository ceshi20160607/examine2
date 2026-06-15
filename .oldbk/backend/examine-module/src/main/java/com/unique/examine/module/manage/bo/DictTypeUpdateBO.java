package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典类型更新入参。
 */
@Data
@Schema(description = "字典类型更新入参")
public class DictTypeUpdateBO {

    @Schema(description = "字典类型名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "状态：ENABLED、DISABLED")
    private String status;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "版本号，使用 cacheVersion")
    private Long version;
}
