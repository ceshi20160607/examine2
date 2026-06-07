package com.unique.examine.module.manage.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行态模块 schema。
 */
@Data
@Builder
@Schema(description = "运行态模块 schema")
public class RuntimeModuleSchemaVO {

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "发布版本 ID")
    private String publishedVersionId;

    @Schema(description = "列表 schema")
    private JsonNode listSchema;

    @Schema(description = "表单 schema")
    private JsonNode formSchema;

    @Schema(description = "详情 schema")
    private JsonNode detailSchema;

    @Schema(description = "字段定义")
    private JsonNode fieldDefinitions;

    @Schema(description = "可用操作")
    private List<ActionConfigVO> availableActions;

    @Schema(description = "权限提示")
    private List<String> permissionHints;

    @Schema(description = "状态规则")
    private JsonNode statusRules;
}
