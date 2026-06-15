package com.unique.examine.app.manage.vo;

import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.ClientCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OpenAPI 已验签请求上下文。
 */
@Data
@Builder
@Schema(description = "OpenAPI 已验签请求上下文")
public class OpenApiRequestContext {

    @Schema(description = "调用日志 ID")
    private Long logId;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "API ID")
    private String apiId;

    @Schema(description = "scope")
    private String scopeCode;

    @Schema(description = "客户端")
    private Client client;

    @Schema(description = "凭证")
    private ClientCredential credential;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "来源 IP")
    private String sourceIp;

    @Schema(description = "开始时间纳秒")
    private long startNanoTime;
}
