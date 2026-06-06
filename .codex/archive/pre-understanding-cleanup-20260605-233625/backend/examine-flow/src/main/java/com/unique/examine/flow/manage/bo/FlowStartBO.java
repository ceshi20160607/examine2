package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发起流程入参。
 */
@Data
@Schema(description = "发起流程入参")
public class FlowStartBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "业务记录 ID")
    private Long recordId;

    @Schema(description = "流程模板 ID")
    private Long templateId;

    @Schema(description = "流程模板版本 ID，为空时使用模板当前发布版本")
    private Long templateVersionId;

    @Schema(description = "首个任务处理人账号 ID")
    private Long assigneeId;

    @Schema(description = "首个任务名称")
    private String taskName;
}
