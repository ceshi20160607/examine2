package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程模板保存入参。
 */
@Data
@Schema(description = "流程模板保存入参")
public class FlowTemplateSaveBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;
}
