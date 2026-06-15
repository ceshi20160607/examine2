package com.unique.examine.flow.manage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程图回显。
 */
@Data
@Builder
@Schema(description = "流程图回显")
public class FlowTemplateGraphVO {

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "发布版本 ID")
    private String templateVersionId;

    @Schema(description = "流程图")
    private JsonNode graph;

    @Schema(description = "是否草稿")
    private Boolean draft;
}
