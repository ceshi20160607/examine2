package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流程任务处理入参")
public class WorkflowTaskActionBO {
    @NotBlank private String action;
    @Schema(description = "处理意见") private String comment;
    @Schema(description = "转交目标账号ID") private Long transferTo;
}

