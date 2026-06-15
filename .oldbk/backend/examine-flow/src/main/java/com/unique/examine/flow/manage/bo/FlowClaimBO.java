package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程领取入参。
 */
@Data
@Schema(description = "流程领取入参")
public class FlowClaimBO {

    @NotNull(message = "任务版本不能为空")
    @Schema(description = "任务版本")
    private Integer taskVersion;

    @Schema(description = "幂等键")
    private String idempotencyKey;
}
