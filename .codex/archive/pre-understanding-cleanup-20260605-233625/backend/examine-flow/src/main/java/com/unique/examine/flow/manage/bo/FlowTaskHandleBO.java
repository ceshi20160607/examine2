package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程任务处理入参。
 */
@Data
@Schema(description = "流程任务处理入参")
public class FlowTaskHandleBO {

    @Schema(description = "处理动作：APPROVE、REJECT、TRANSFER、CANCEL")
    private String actionType;

    @Schema(description = "处理意见")
    private String commentText;

    @Schema(description = "转交目标账号 ID，仅 TRANSFER 使用")
    private Long transferTo;
}
