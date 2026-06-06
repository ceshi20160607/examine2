package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAPI 凭证创建入参。
 */
@Data
@Schema(description = "OpenAPI 凭证创建入参")
public class OpenApiCredentialCreateBO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "访问 key")
    private String accessKey;

    @Schema(description = "密钥明文，MVP 保存为摘要占位，生产需替换为安全哈希")
    private String secret;

    @Schema(description = "签名算法，默认 HMAC_SHA256")
    private String signAlgorithm;
}
