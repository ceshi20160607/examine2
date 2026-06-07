package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OpenAPI 记录更新入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "OpenAPI 记录更新入参")
public class OpenApiRecordUpdateBO extends OpenApiRecordSaveBO {

    @NotNull(message = "记录版本不能为空")
    @Schema(description = "记录乐观锁版本")
    private Integer recordVersion;
}
