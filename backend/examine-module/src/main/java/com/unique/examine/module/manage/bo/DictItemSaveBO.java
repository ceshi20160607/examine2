package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项新增入参。
 */
@Data
@Schema(description = "字典项新增入参")
public class DictItemSaveBO {

    @Schema(description = "父字典项 ID，空或 0 表示根项")
    private String parentId;

    @NotBlank(message = "字典项编码不能为空")
    @Schema(description = "字典项编码")
    private String code;

    @NotBlank(message = "展示文本不能为空")
    @Schema(description = "展示文本")
    private String label;

    @NotBlank(message = "业务值不能为空")
    @Schema(description = "业务值")
    private String value;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "状态：ENABLED、DISABLED")
    private String status;

    @Schema(description = "扩展 JSON")
    private String ext;
}
