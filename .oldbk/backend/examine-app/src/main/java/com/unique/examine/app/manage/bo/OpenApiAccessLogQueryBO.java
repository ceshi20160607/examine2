package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAPI 调用日志查询入参。
 */
@Data
@Schema(description = "OpenAPI 调用日志查询入参")
public class OpenApiAccessLogQueryBO {

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "API ID")
    private String apiId;

    @Schema(description = "错误码")
    private String errorCode;
}
