package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * OpenAPI 记录提交入参。
 */
@Data
@Schema(description = "OpenAPI 记录提交入参")
public class OpenApiRecordSubmitBO {

    @NotBlank(message = "模块编码不能为空")
    @Schema(description = "模块编码")
    private String moduleCode;

    @NotNull(message = "记录版本不能为空")
    @Schema(description = "记录乐观锁版本")
    private Integer recordVersion;

    @Schema(description = "提交说明")
    private String reason;
}
