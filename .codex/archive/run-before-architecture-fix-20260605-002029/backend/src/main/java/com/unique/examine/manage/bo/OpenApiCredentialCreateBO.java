package com.unique.examine.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "OpenAPI凭证创建入参")
public class OpenApiCredentialCreateBO {
    @NotNull
    @Schema(description = "OpenAPI应用主键")
    private Long clientPk;
    @Schema(description = "整数密钥版本；为空时后端生成")
    private String keyVersion;
    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;
}
