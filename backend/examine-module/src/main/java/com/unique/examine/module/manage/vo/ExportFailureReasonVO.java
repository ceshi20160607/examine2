package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 导出失败原因。
 */
@Data
@Builder
@Schema(description = "导出失败原因")
public class ExportFailureReasonVO {

    @Schema(description = "失败编码")
    private String code;

    @Schema(description = "失败提示")
    private String message;

    @Schema(description = "是否可重试")
    private Boolean retryable;

    @Schema(description = "堆栈摘要")
    private String stackSummary;

    @Schema(description = "失败时间")
    private LocalDateTime failedAt;
}
