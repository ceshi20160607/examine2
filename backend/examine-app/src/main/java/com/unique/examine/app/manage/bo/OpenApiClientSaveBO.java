package com.unique.examine.app.manage.bo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * OpenAPI 客户端保存入参。
 */
@Data
@Schema(description = "OpenAPI 客户端保存入参")
public class OpenApiClientSaveBO {

    @NotNull(message = "租户 ID 不能为空")
    @Schema(description = "绑定租户 ID")
    private Long tenantId;

    @NotBlank(message = "客户端编码不能为空")
    @Schema(description = "客户端编码")
    private String code;

    @NotBlank(message = "客户端名称不能为空")
    @Schema(description = "客户端名称")
    private String name;

    @Schema(description = "DRAFT、ENABLED、DISABLED、EXPIRED")
    private String status;

    @Schema(description = "客户端默认数据范围快照")
    private String dataScopeJson;

    @Schema(description = "客户端过期时间")
    private LocalDateTime expiresAt;

    @Valid
    @Schema(description = "scope 授权")
    private List<OpenApiScopeSaveBO> scopes;

    @Valid
    @Schema(description = "IP 白名单")
    private List<OpenApiIpWhitelistBO> ipWhitelist;

    @Valid
    @Schema(description = "限流策略")
    private List<OpenApiRateLimitPolicyDTO> rateLimitPolicy;
}
