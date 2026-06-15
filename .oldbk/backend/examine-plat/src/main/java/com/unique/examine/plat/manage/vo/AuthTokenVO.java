package com.unique.examine.plat.manage.vo;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 认证 token 回显。
 */
@Data
@Builder
@Schema(description = "认证 token 回显")
public class AuthTokenVO {

    @Schema(description = "平台账号 ID")
    private String accountId;

    @Schema(description = "登录名")
    private String loginName;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "内部 API 访问 token")
    private String accessToken;

    @Schema(description = "刷新 token")
    private String refreshToken;

    @Schema(description = "accessToken 过期时间")
    private OffsetDateTime accessTokenExpiresAt;

    @Schema(description = "refreshToken 过期时间")
    private OffsetDateTime refreshTokenExpiresAt;
}
