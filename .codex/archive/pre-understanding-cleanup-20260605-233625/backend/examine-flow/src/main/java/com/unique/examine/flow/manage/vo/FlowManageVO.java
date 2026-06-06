package com.unique.examine.flow.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程管理统一出参。
 */
@Data
@Schema(description = "流程管理统一出参")
public class FlowManageVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "业务记录 ID")
    private Long recordId;

    @Schema(description = "编码或节点 key")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "流程模板 ID")
    private Long templateId;

    @Schema(description = "模板版本 ID")
    private Long templateVersionId;

    @Schema(description = "任务处理人账号 ID")
    private Long assigneeId;

    @Schema(description = "流程图或快照 JSON")
    private String graphJson;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间或处理时间")
    private LocalDateTime updatedAt;
}
