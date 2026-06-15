package com.unique.examine.flow.manage.bo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程条件入参。
 */
@Data
@Schema(description = "流程条件入参")
public class FlowTemplateConditionBO {

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "条件操作符")
    private String operator;

    @Schema(description = "比较值")
    private JsonNode compareValue;

    @Schema(description = "复杂表达式")
    private JsonNode expression;

    @Schema(description = "排序")
    private Integer sortOrder;
}
