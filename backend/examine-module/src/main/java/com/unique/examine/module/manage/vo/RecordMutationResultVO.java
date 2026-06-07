package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录写操作结果。
 */
@Data
@Builder
@Schema(description = "运行记录写操作结果")
public class RecordMutationResultVO {

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "记录状态")
    private String recordStatus;

    @Schema(description = "记录版本")
    private Integer recordVersion;

    @Schema(description = "流程实例 ID")
    private String flowInstanceId;

    @Schema(description = "变更字段编码")
    private List<String> changedFields;

    @Schema(description = "是否幂等重放")
    private Boolean idempotencyReplay;

    @Schema(description = "可用操作")
    private List<ActionConfigVO> availableActions;
}
