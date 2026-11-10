package com.unique.unexamine.sys.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 系统操作日志的 HTTP 返回模型，避免把基础 Entity 直接暴露给调用方。 */
public final class SystemLogDto {
    private SystemLogDto() {}

    /** 管理员可见的日志字段；删除标记等持久化内部字段不作为接口契约。 */
    public record LogView(
            @Schema(description = "日志主键") Long id,
            @Schema(description = "所属系统主键") Long systemId,
            @Schema(description = "操作租户主键") Long tenantId,
            @Schema(description = "操作账号主键") Long accountId,
            @Schema(description = "操作类型编码") String actionCode,
            @Schema(description = "操作对象类型") String objectType,
            @Schema(description = "操作对象主键") Long objectId,
            @Schema(description = "操作摘要和权限影响范围 JSON") String detailJson,
            @Schema(description = "请求追踪标识") String traceId,
            @Schema(description = "操作时间") LocalDateTime createTime) {}

    /** 显式进入系统的登录记录。 */
    public record LoginView(Long id, Long systemId, Long tenantId, Long accountId,
                            String resultCode, String failureReason, String ipAddress,
                            String traceId, LocalDateTime createTime) {}
}
