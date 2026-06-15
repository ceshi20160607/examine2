package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模块保存入参。
 */
@Data
@Schema(description = "模块保存入参")
public class ModuleSaveBO {

    @NotBlank(message = "模块名称不能为空")
    @Schema(description = "模块名称")
    private String name;

    @NotBlank(message = "模块编码不能为空")
    @Schema(description = "模块编码，同应用下唯一")
    private String code;

    @Schema(description = "模块描述")
    private String description;

    @Schema(description = "记录标题字段 ID")
    private String titleFieldId;

    @Schema(description = "记录编号字段 ID")
    private String recordNoFieldId;

    @Schema(description = "排序")
    private Integer sortOrder;
}
