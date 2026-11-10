package com.unique.unexamine.app.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 应用、密钥、授权快照和事件配置管理模型。 */
public final class AppManageDto {
    private AppManageDto() {}

    /** 保存应用草稿；secret 只在创建或主动轮换时接收。 */
    public record SaveRequest(
            @Schema(description = "已有应用主键；新增时为空") Long id,
            @NotBlank @Size(max = 100) @Schema(description = "稳定应用标识") String appId,
            @NotBlank @Size(max = 120) @Schema(description = "应用名称") String name,
            @Size(max = 500) @Schema(description = "应用说明") String description,
            @NotBlank @Size(max = 30) @Schema(description = "INTERNAL、EXTERNAL 或 BOTH") String accessMode,
            @Size(max = 500) @Schema(description = "回调地址") String callbackUrl,
            @Schema(description = "资源动作、字段和数据范围授权配置") JsonNode config,
            @Size(min = 16, max = 200) @Schema(description = "创建或轮换时使用的新密钥") String secret) {}

    /** 应用事件草稿。 */
    public record EventRequest(
            @Schema(description = "已有事件主键；新增时为空") Long id,
            @NotBlank @Size(max = 100) @Schema(description = "事件编码") String eventCode,
            @NotBlank @Size(max = 40) @Schema(description = "事件资源类型") String resourceType,
            @Schema(description = "具体资源主键；为空表示资源类型级事件") Long resourceId,
            @Size(max = 500) @Schema(description = "事件回调地址") String callbackUrl,
            @Schema(description = "签名、重试和过滤配置") JsonNode config) {}

    /** 应用非敏感配置；issuedSecret 仅在创建或轮换本次返回一次。 */
    public record AppView(Long id, String scopeType, Long systemId, Long tenantId, String appId,
                          String name, String description, String accessMode, String callbackUrl,
                          String status, Integer secretVersion, Integer publishedVersion,
                          JsonNode config, JsonNode publishedConfig, String issuedSecret,
                          LocalDateTime updateTime) {}

    /** 应用事件配置及发布状态。 */
    public record EventView(Long id, Long applicationId, String eventCode, String resourceType,
                            Long resourceId, String callbackUrl, String status, JsonNode config,
                            JsonNode publishedConfig, LocalDateTime updateTime) {}
}
