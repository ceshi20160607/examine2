package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字段静态选项回显。
 */
@Data
@Builder
@Schema(description = "字段静态选项回显")
public class FieldOptionVO {

    @Schema(description = "选项 ID")
    private String optionId;

    @Schema(description = "字段 ID")
    private String fieldId;

    @Schema(description = "选项编码")
    private String code;

    @Schema(description = "展示文本")
    private String label;

    @Schema(description = "选项值")
    private String value;

    @Schema(description = "颜色标识")
    private String color;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
