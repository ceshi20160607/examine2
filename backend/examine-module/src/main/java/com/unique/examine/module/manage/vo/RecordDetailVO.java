package com.unique.examine.module.manage.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录详情。
 */
@Data
@Builder
@Schema(description = "运行记录详情")
public class RecordDetailVO {

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "记录状态")
    private String recordStatus;

    @Schema(description = "记录版本")
    private Integer recordVersion;

    @Schema(description = "字段值")
    private List<RecordValueVO> values;

    @Schema(description = "文件引用，BE-010 补齐")
    private List<JsonNode> fileRefs;

    @Schema(description = "流程摘要，BE-009 补齐")
    private JsonNode flowSummary;

    @Schema(description = "历史摘要")
    private JsonNode historySummary;

    @Schema(description = "可用操作")
    private List<ActionConfigVO> availableActions;

    @Schema(description = "字段权限，BE-014 权限服务提供")
    private List<JsonNode> fieldPermissions;

    @Schema(description = "审计字段")
    private JsonNode auditFields;
}
