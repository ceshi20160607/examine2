package com.unique.examine.core.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 响应元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API 响应元数据")
public class ApiResponseMeta {

    @Schema(description = "请求级追踪 ID")
    private String requestId;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    @Schema(description = "请求路径")
    private String path;

    @Schema(description = "请求方法")
    private String method;

    @Schema(description = "幂等键")
    private String idempotencyKey;

    @Schema(description = "请求哈希")
    private String requestHash;

    @Schema(description = "是否为幂等回放")
    private Boolean idempotencyReplay;

    @Schema(description = "结果快照 ID")
    private String resultSnapshotId;
}
