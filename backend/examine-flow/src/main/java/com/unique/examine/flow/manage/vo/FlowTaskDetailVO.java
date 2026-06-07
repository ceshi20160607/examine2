package com.unique.examine.flow.manage.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程任务详情。
 */
@Data
@Builder
@Schema(description = "流程任务详情")
public class FlowTaskDetailVO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "任务版本")
    private Integer taskVersion;

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "记录摘要")
    private JsonNode recordSummary;

    @Schema(description = "表单 schema")
    private JsonNode formSchema;

    @Schema(description = "字段值")
    private JsonNode values;

    @Schema(description = "历史")
    private List<FlowHistoryItemVO> history;

    @Schema(description = "流程图")
    private JsonNode diagram;

    @Schema(description = "可用动作")
    private List<String> availableActions;
}
