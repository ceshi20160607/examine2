package com.unique.examine.core.common.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 错误明细，承载字段级或对象级错误信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API 错误明细")
public class ApiErrorDetail {

    @Schema(description = "单条错误明细 ID")
    @Builder.Default
    private String errorId = "err_" + UUID.randomUUID().toString().replace("-", "");

    @Schema(description = "错误目标类型，例如 FIELD、RECORD、PERMISSION、STATE、OPENAPI、FILE、EXPORT、FLOW")
    private String targetType;

    @Schema(description = "字段级错误对应的字段编码")
    private String fieldCode;

    @Schema(description = "业务对象类型")
    private String objectType;

    @Schema(description = "业务对象 ID")
    private String objectId;

    @Schema(description = "稳定机器可读错误原因")
    private String reason;

    @Schema(description = "期望值或允许范围")
    private Object expected;

    @Schema(description = "实际值，敏感字段需由调用方脱敏后传入")
    private Object actual;

    @Schema(description = "是否建议原请求重试")
    private boolean retryable;

    @Schema(description = "前端可直接展示的短提示")
    private String userMessage;
}
