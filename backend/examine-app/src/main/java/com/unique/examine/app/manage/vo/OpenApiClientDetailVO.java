package com.unique.examine.app.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.unique.examine.app.manage.bo.OpenApiRateLimitPolicyDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI 客户端详情。
 */
@Data
@Builder
@Schema(description = "OpenAPI 客户端详情")
public class OpenApiClientDetailVO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "accessKey")
    private String accessKey;

    @Schema(description = "secret 仅创建或轮换时返回")
    private String secretOnce;

    @Schema(description = "脱敏 secret")
    private String maskedSecret;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "客户端编码")
    private String code;

    @Schema(description = "客户端名称")
    private String name;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "数据范围 JSON")
    private String dataScopeJson;

    @Schema(description = "客户端过期时间")
    private LocalDateTime expiresAt;

    @Schema(description = "最近调用时间")
    private LocalDateTime lastUsedAt;

    @Schema(description = "scope 授权")
    private List<OpenApiScopeVO> scopes;

    @Schema(description = "IP 白名单")
    private List<String> ipWhitelist;

    @Schema(description = "限流策略")
    private List<OpenApiRateLimitPolicyDTO> rateLimitPolicy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
