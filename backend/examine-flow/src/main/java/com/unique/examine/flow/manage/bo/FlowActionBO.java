package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程任务处理入参。
 */
@Data
@Schema(description = "流程任务处理入参")
public class FlowActionBO {

    @NotBlank(message = "流程动作不能为空")
    @Schema(description = "APPROVE、REJECT、TRANSFER、RETURN、TERMINATE")
    private String action;

    @Schema(description = "审批意见")
    private String comment;

    @Schema(description = "转交目标成员")
    private Long targetMemberId;

    @NotNull(message = "任务版本不能为空")
    @Schema(description = "任务版本")
    private Integer taskVersion;

    @Schema(description = "幂等键")
    private String idempotencyKey;
}
