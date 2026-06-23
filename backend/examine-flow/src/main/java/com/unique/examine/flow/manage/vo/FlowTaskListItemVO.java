package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程任务列表项。
 */
@Data
@Builder
@Schema(description = "流程任务列表项")
public class FlowTaskListItemVO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "节点编码")
    private String nodeKey;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务状态")
    private String taskStatus;

    @Schema(description = "任务版本")
    private Integer taskVersion;

    @Schema(description = "记录摘要")
    private JsonNode recordSummary;

    @Schema(description = "到期时间")
    private LocalDateTime dueAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
