package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字段选项 DTO。
 */
@Data
@Schema(description = "字段选项 DTO")
public class ModuleFieldOptionDTO {

    @Schema(description = "选项值")
    private String optionValue;

    @Schema(description = "选项显示名")
    private String optionLabel;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
