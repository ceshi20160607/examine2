package com.unique.examine.app.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI 凭证一次性展示。
 */
@Data
@Builder
@Schema(description = "OpenAPI 凭证一次性展示")
public class OpenApiCredentialOnceVO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "accessKey")
    private String accessKey;

    @Schema(description = "secret 仅本次返回")
    private String secretOnce;

    @Schema(description = "脱敏 secret")
    private String maskedSecret;
}
