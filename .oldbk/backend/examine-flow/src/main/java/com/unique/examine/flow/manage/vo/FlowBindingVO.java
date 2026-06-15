package com.unique.examine.flow.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程绑定回显。
 */
@Data
@Builder
@Schema(description = "流程绑定回显")
public class FlowBindingVO {

    @Schema(description = "绑定 ID")
    private String bindingId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "触发动作")
    private String actionCode;

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "模板版本 ID")
    private String templateVersionId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "版本")
    private Integer versionNo;
}
