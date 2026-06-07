package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字段静态选项入参。
 */
@Data
@Schema(description = "字段静态选项入参")
public class FieldOptionBO {

    @NotBlank(message = "选项编码不能为空")
    @Schema(description = "选项编码")
    private String code;

    @NotBlank(message = "选项展示文本不能为空")
    @Schema(description = "选项展示文本")
    private String label;

    @NotBlank(message = "选项值不能为空")
    @Schema(description = "选项值")
    private String value;

    @Schema(description = "颜色标识")
    private String color;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
