package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程撤回入参。
 */
@Data
@Schema(description = "流程撤回入参")
public class FlowWithdrawBO {

    @NotBlank(message = "撤回原因不能为空")
    @Schema(description = "撤回原因")
    private String reason;

    @Schema(description = "幂等键")
    private String idempotencyKey;
}
