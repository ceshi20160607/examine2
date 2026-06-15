package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OpenAPI IP 白名单入参。
 */
@Data
@Schema(description = "OpenAPI IP 白名单入参")
public class OpenApiIpWhitelistBO {

    @NotBlank(message = "IP 规则不能为空")
    @Schema(description = "IP 或 CIDR")
    private String ipRule;

    @Schema(description = "IP、CIDR")
    private String ruleType;

    @Schema(description = "ENABLED、DISABLED")
    private String status;

    @Schema(description = "说明")
    private String description;
}
