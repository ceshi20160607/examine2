package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程模板保存入参。
 */
@Data
@Schema(description = "流程模板保存入参")
public class FlowTemplateSaveBO {

    @NotBlank(message = "流程模板编码不能为空")
    @Schema(description = "流程模板编码")
    private String code;

    @NotBlank(message = "流程模板名称不能为空")
    @Schema(description = "流程模板名称")
    private String name;

    @Schema(description = "流程模板说明")
    private String description;
}
