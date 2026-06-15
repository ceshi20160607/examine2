package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程模板状态变更入参。
 */
@Data
@Schema(description = "流程模板状态变更入参")
public class FlowTemplateStatusBO {

    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "ENABLED、DISABLED")
    private String targetStatus;

    @Schema(description = "原因")
    private String reason;

    @Schema(description = "模板版本")
    private Integer versionNo;
}
