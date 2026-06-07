package com.unique.examine.flow.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程连线入参。
 */
@Data
@Schema(description = "流程连线入参")
public class FlowTemplateLineBO {

    @NotBlank(message = "连线编码不能为空")
    @Schema(description = "连线编码")
    private String lineKey;

    @NotBlank(message = "起点节点不能为空")
    @Schema(description = "起点节点")
    private String fromNodeKey;

    @NotBlank(message = "终点节点不能为空")
    @Schema(description = "终点节点")
    private String toNodeKey;

    @Schema(description = "ALWAYS、EXPRESSION")
    private String conditionMode;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Valid
    @Schema(description = "结构化条件")
    private List<FlowTemplateConditionBO> conditions;
}
