package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 导出任务动作入参。
 */
@Data
@Schema(description = "导出任务动作入参")
public class ExportJobActionBO {

    @Schema(description = "动作原因")
    private String reason;

    @Schema(description = "幂等键")
    private String idempotencyKey;
}
