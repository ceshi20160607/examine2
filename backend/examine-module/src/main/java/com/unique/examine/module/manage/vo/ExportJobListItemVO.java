package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 导出任务列表项。
 */
@Data
@SuperBuilder
@Schema(description = "导出任务列表项")
public class ExportJobListItemVO {

    @Schema(description = "任务 ID")
    private String jobId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "进度")
    private Integer progress;

    @Schema(description = "结果文件 ID")
    private String resultFileId;

    @Schema(description = "结果文件名")
    private String fileName;

    @Schema(description = "失败原因")
    private ExportFailureReasonVO failureReason;

    @Schema(description = "是否可重试")
    private Boolean retryable;

    @Schema(description = "创建人成员 ID")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;
}
