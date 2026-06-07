package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OpenAPI 客户端状态入参。
 */
@Data
@Schema(description = "OpenAPI 客户端状态入参")
public class OpenApiClientStatusBO {

    @NotBlank(message = "状态不能为空")
    @Schema(description = "DRAFT、ENABLED、DISABLED、EXPIRED")
    private String status;
}
