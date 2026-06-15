package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模块更新入参。
 */
@Data
@Schema(description = "模块更新入参")
public class ModuleUpdateBO {

    @Schema(description = "模块名称")
    private String name;

    @Schema(description = "模块描述")
    private String description;

    @Schema(description = "记录标题字段 ID")
    private String titleFieldId;

    @Schema(description = "记录编号字段 ID")
    private String recordNoFieldId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "乐观锁版本")
    private Integer version;
}
