package com.unique.examine.flow.manage.bo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 流程图保存入参。
 */
@Data
@Schema(description = "流程图保存入参")
public class FlowTemplateGraphBO {

    @Valid
    @NotEmpty(message = "流程节点不能为空")
    @Schema(description = "流程节点")
    private List<FlowTemplateNodeBO> nodes;

    @Valid
    @Schema(description = "流程连线")
    private List<FlowTemplateLineBO> lines;

    @Schema(description = "前端画布布局")
    private JsonNode layout;
}
