package com.unique.unexamine.plat.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 平台操作日志的 HTTP 返回模型，不暴露逻辑删除字段。 */
public final class PlatformLogDto {
    private PlatformLogDto() {}

    /** 平台管理员可见的日志摘要。 */
    public record LogView(
            @Schema(description = "日志主键") Long id,
            @Schema(description = "操作账号主键") Long accountId,
            @Schema(description = "操作类型编码") String actionCode,
            @Schema(description = "操作对象类型") String objectType,
            @Schema(description = "操作对象主键") Long objectId,
            @Schema(description = "操作摘要 JSON") String detailJson,
            @Schema(description = "请求追踪标识") String traceId,
            @Schema(description = "操作时间") LocalDateTime createTime) {}
}
