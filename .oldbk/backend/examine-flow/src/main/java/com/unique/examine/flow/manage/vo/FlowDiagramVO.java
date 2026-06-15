package com.unique.examine.flow.manage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程实例图。
 */
@Data
@Builder
@Schema(description = "流程实例图")
public class FlowDiagramVO {

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "模板版本 ID")
    private String templateVersionId;

    @Schema(description = "当前节点")
    private String currentNodeKeys;

    @Schema(description = "流程图")
    private JsonNode graph;
}
