package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录列表项。
 */
@Data
@Builder
@Schema(description = "运行记录列表项")
public class RecordListItemVO {

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "记录编号")
    private String recordNo;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "记录状态")
    private String recordStatus;

    @Schema(description = "流程状态")
    private String flowStatus;

    @Schema(description = "字段值摘要")
    private List<RecordValueVO> values;

    @Schema(description = "可用操作")
    private List<ActionConfigVO> availableActions;

    @Schema(description = "创建人名称")
    private String createdByName;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "记录版本")
    private Integer recordVersion;
}
