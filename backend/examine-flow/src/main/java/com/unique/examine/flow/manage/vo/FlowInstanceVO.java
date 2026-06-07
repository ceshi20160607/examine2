package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程实例回显。
 */
@Data
@Builder
@Schema(description = "流程实例回显")
public class FlowInstanceVO {

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "模板版本 ID")
    private String templateVersionId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "发起人")
    private String starterMemberId;

    @Schema(description = "当前节点")
    private String currentNodeKeys;

    @Schema(description = "版本")
    private Integer versionNo;

    @Schema(description = "发起时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
