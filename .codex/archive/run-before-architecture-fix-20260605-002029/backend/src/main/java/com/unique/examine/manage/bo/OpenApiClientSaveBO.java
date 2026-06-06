package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenAPI应用保存入参")
public class OpenApiClientSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotBlank private String clientId;
    @NotBlank private String clientName;
    @Schema(description = "限流规则JSON") private String rateLimitRule;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

