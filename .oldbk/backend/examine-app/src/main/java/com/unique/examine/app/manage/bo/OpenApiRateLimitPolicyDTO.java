package com.unique.examine.app.manage.bo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * OpenAPI 限流策略入参。
 */
@Data
@Schema(description = "OpenAPI 限流策略入参")
public class OpenApiRateLimitPolicyDTO {

    @Schema(description = "API ID，为空表示客户端默认策略")
    private String apiId;

    @Schema(description = "scope，为空表示通用策略")
    private String scopeCode;

    @Schema(description = "来源 IP 限定")
    private String sourceIp;

    @Min(value = 1, message = "限流窗口秒数必须大于 0")
    @Schema(description = "限流窗口秒数")
    private Integer windowSeconds;

    @Min(value = 1, message = "窗口最大请求数必须大于 0")
    @Schema(description = "窗口最大请求数")
    private Integer maxRequests;

    @Min(value = 0, message = "突发额度不能小于 0")
    @Schema(description = "突发额度")
    private Integer burst;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间")
    private LocalDateTime effectiveTo;

    @Schema(description = "ENABLED、DISABLED")
    private String status;
}
