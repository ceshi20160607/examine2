package com.unique.examine.app.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI 调用日志返回。
 */
@Data
@Builder
@Schema(description = "OpenAPI 调用日志返回")
public class OpenApiAccessLogVO {

    @Schema(description = "日志 ID")
    private Long logId;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "accessKey")
    private String accessKey;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "API ID")
    private String apiId;

    @Schema(description = "HTTP 方法")
    private String method;

    @Schema(description = "请求路径")
    private String path;

    @Schema(description = "HTTP 状态码")
    private Integer statusCode;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "签名结果")
    private String signatureResult;

    @Schema(description = "nonce 结果")
    private String nonceResult;

    @Schema(description = "幂等结果")
    private String idempotencyResult;

    @Schema(description = "限流结果")
    private String rateLimitResult;

    @Schema(description = "scope 结果")
    private String scopeResult;

    @Schema(description = "耗时毫秒")
    private Integer durationMs;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
