package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OpenAPI IP 白名单保存入参。
 */
@Data
@Schema(description = "OpenAPI IP 白名单保存入参")
public class OpenApiIpWhitelistSaveBO {

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "IP 或 CIDR")
    private String ipValue;
}
