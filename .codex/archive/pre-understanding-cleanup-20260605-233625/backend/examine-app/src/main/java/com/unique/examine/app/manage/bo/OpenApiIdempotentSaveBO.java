package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OpenAPI 幂等记录保存入参。
 */
@Data
@Schema(description = "OpenAPI 幂等记录保存入参")
public class OpenApiIdempotentSaveBO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "幂等 key")
    private String idempotentKey;

    @Schema(description = "请求摘要")
    private String requestHash;

    @Schema(description = "响应摘要")
    private String responseHash;

    @Schema(description = "过期时间")
    private LocalDateTime expiredAt;
}
