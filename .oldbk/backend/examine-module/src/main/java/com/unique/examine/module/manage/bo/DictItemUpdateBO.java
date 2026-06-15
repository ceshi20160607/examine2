package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典项更新入参。
 */
@Data
@Schema(description = "字典项更新入参")
public class DictItemUpdateBO {

    @Schema(description = "父字典项 ID")
    private String parentId;

    @Schema(description = "展示文本")
    private String label;

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

    @NotNull(message = "版本号不能为空")
    @Schema(description = "版本号，使用 cacheVersion")
    private Long version;
}
