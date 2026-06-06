package com.unique.examine.core.security;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 当前登录与租户上下文。
 */
@Data
@Builder
@Schema(description = "当前登录与租户上下文")
public class AuthContext {

    @Schema(description = "账号 ID")
    private Long accountId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;
}
