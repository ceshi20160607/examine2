package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模块流程绑定入参。
 */
@Data
@Schema(description = "模块流程绑定入参")
public class FlowBindingSaveBO {

    @NotNull(message = "流程模板 ID 不能为空")
    @Schema(description = "流程模板 ID")
    private Long templateId;

    @NotNull(message = "流程发布版本 ID 不能为空")
    @Schema(description = "流程发布版本 ID")
    private Long templateVersionId;

    @Schema(description = "触发动作")
    private String actionCode = "RECORD_SUBMIT";

    @Schema(description = "绑定状态")
    private String status = "ENABLED";

    @Schema(description = "绑定版本")
    private Integer versionNo;
}
