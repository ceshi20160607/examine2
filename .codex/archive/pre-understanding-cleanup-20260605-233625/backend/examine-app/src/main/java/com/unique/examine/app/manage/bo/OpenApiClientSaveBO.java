package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OpenAPI 客户端保存入参。
 */
@Data
@Schema(description = "OpenAPI 客户端保存入参")
public class OpenApiClientSaveBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "客户端编码")
    private String clientCode;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "每分钟限流")
    private Integer rateLimitPerMinute;

    @Schema(description = "过期时间")
    private LocalDateTime expiredAt;
}
