package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典类型新增入参。
 */
@Data
@Schema(description = "字典类型新增入参")
public class DictTypeSaveBO {

    @NotBlank(message = "作用域不能为空")
    @Schema(description = "作用域：SYSTEM、TENANT")
    private String scopeType;

    @Schema(description = "租户 ID，scopeType=TENANT 时必填")
    private String tenantId;

    @NotBlank(message = "字典类型编码不能为空")
    @Schema(description = "字典类型编码")
    private String code;

    @NotBlank(message = "字典类型名称不能为空")
    @Schema(description = "字典类型名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "状态：ENABLED、DISABLED")
    private String status;
}
