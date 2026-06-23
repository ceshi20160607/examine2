package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程历史项。
 */
@Data
@Builder
@Schema(description = "流程历史项")
public class FlowHistoryItemVO {

    @Schema(description = "日志 ID")
    private String logId;

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "操作成员")
    private String operatorMemberId;

    @Schema(description = "意见")
    private String comment;

    @Schema(description = "源节点")
    private String fromNodeKey;

    @Schema(description = "目标节点")
    private String toNodeKey;

    @Schema(description = "结果状态")
    private String resultStatus;

    @Schema(description = "请求 ID")
    private String requestId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
