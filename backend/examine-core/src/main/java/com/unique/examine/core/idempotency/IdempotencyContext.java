package com.unique.examine.core.idempotency;

import java.time.Duration;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 幂等判定上下文。
 */
@Data
@Builder
@Schema(description = "幂等判定上下文")
public class IdempotencyContext {

    @Schema(description = "幂等作用域，例如 INTERNAL:accountId:systemId:tenantId:apiId:action:key")
    private String scope;

    @Schema(description = "幂等键")
    private String idempotencyKey;

    @Schema(description = "规范化请求哈希")
    private String requestHash;

    @Schema(description = "幂等判定记录 TTL")
    @Builder.Default
    private Duration ttl = Duration.ofHours(24);

    @Schema(description = "处理中重复请求建议重试等待时间")
    @Builder.Default
    private Duration retryAfter = Duration.ofSeconds(3);
}
