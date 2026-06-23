package com.unique.examine.flow.manage.bo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程节点入参。
 */
@Data
@Schema(description = "流程节点入参")
public class FlowTemplateNodeBO {

    @NotBlank(message = "节点编码不能为空")
    @Schema(description = "节点编码")
    private String nodeKey;

    @NotBlank(message = "节点名称不能为空")
    @Schema(description = "节点名称")
    private String nodeName;

    @NotBlank(message = "节点类型不能为空")
    @Schema(description = "START、APPROVAL、CC、END")
    private String nodeType;

    @Schema(description = "审批人策略")
    private String actorStrategy;

    @Schema(description = "审批人配置")
    private JsonNode actorConfig;

    @Schema(description = "是否要求审批意见")
    private Boolean approvalRequired;

    @Schema(description = "排序")
    private Integer sortOrder;
}
