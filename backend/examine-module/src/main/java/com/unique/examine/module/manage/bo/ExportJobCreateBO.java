package com.unique.examine.module.manage.bo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 导出任务创建入参。
 */
@Data
@Schema(description = "导出任务创建入参")
public class ExportJobCreateBO {

    @Schema(description = "模块 ID")
    @NotNull(message = "模块 ID 不能为空")
    private Long moduleId;

    @Schema(description = "导出模板 ID")
    private Long templateId;

    @Schema(description = "筛选条件快照")
    private List<JsonNode> filters;

    @Schema(description = "排序规则快照")
    private List<JsonNode> sorter;

    @Schema(description = "选中记录 ID，优先于 filters")
    private List<Long> selectedRecordIds;

    @Schema(description = "结果文件名")
    private String fileName;

    @Schema(description = "幂等键")
    private String idempotencyKey;
}
