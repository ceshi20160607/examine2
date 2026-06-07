package com.unique.examine.flow.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程动作结果。
 */
@Data
@Builder
@Schema(description = "流程动作结果")
public class FlowActionResultVO {

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "实例状态")
    private String instanceStatus;

    @Schema(description = "任务状态")
    private String taskStatus;

    @Schema(description = "当前节点")
    private String currentNode;

    @Schema(description = "后续节点")
    private List<String> nextNodes;

    @Schema(description = "业务记录状态")
    private String businessRecordStatus;
}
