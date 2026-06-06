package com.unique.examine.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "流程版本保存入参")
public class WorkflowVersionSaveBO {
    @Schema(description = "系统ID；可不传，后端按流程模板归属补齐")
    private Long systemId;
    @Schema(description = "租户ID；可不传，后端按流程模板归属补齐")
    private Long tenantId;
    @NotNull
    @Schema(description = "流程模板ID")
    private Long templateId;
    @Schema(description = "整数版本号；为空时后端生成")
    private String versionNo;
    @NotBlank
    @Schema(description = "节点JSON")
    private String nodeJson;
    @NotBlank
    @Schema(description = "连线JSON")
    private String edgeJson;
    @Schema(description = "条件JSON")
    private String conditionJson;
    @Schema(description = "流程设置JSON")
    private String settingJson;
}
