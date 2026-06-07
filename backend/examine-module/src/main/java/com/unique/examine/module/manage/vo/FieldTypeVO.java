package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字段类型回显。
 */
@Data
@Builder
@Schema(description = "字段类型回显")
public class FieldTypeVO {

    @Schema(description = "字段类型编码")
    private String code;

    @Schema(description = "字段类型名称")
    private String name;

    @Schema(description = "是否支持唯一")
    private Boolean uniqueSupported;

    @Schema(description = "是否支持选项")
    private Boolean optionSupported;

    @Schema(description = "是否支持字典")
    private Boolean dictSupported;
}
