package com.unique.examine.app.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAPI 访问日志查询 DTO。
 */
@Data
@Schema(description = "OpenAPI 访问日志查询 DTO")
public class OpenApiAccessLogQueryDTO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "调用状态")
    private String status;
}
