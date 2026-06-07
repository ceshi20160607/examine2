package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典删除入参。
 */
@Data
@Schema(description = "字典删除入参")
public class DictDeleteBO {

    @Schema(description = "是否强制删除，MVP 固定 false")
    private Boolean force;

    @Schema(description = "删除原因")
    private String reason;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "版本号，使用 cacheVersion")
    private Long version;
}
