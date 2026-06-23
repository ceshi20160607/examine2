package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 导出任务、筛选快照、权限快照、结果文件引用和重试状态。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_job")
@Schema(name = "ExportJob", description = "导出任务、筛选快照、权限快照、结果文件引用和重试状态。")
public class ExportJob implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导出任务 ID。")
    @TableId(value = "job_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "导出模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "导出模板；允许使用默认模板时为空。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "创建任务时的发布版本。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishVersionId;

    @Schema(description = "导出任务状态。")
    private String jobStatus;

    @Schema(description = "进度 0-100。")
    private Integer progress;

    @Schema(description = "选中记录 ID 快照；优先于筛选条件。")
    private String selectedRecordIdsJson;

    @Schema(description = "筛选条件快照。")
    private String filterSnapshotJson;

    @Schema(description = "排序快照。")
    private String sorterSnapshotJson;

    @Schema(description = "导出字段和发布字段定义快照。")
    private String fieldSnapshotJson;

    @Schema(description = "字段可见、导出明文、操作权限、数据范围和脱敏权限快照。")
    private String permissionSnapshotJson;

    @Schema(description = "数据范围命中规则快照。")
    private String dataScopeSnapshotJson;

    @Schema(description = "结果文件名。")
    private String fileName;

    @Schema(description = "导出结果文件逻辑引用，对应 un_upload_ 文件主表。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resultFileId;

    @Schema(description = "失败错误码。")
    private String failureCode;

    @Schema(description = "失败提示。")
    private String failureMessage;

    @Schema(description = "failureReason，含 retryable、stackSummary、failedAt。")
    private String failureSnapshotJson;

    @Schema(description = "是否可重试。")
    private Byte retryableFlag;

    @Schema(description = "已重试次数。")
    private Integer retryCount;

    @Schema(description = "最大重试次数。")
    private Integer maxRetryCount;

    @Schema(description = "后台 runner 标识。")
    private String claimedBy;

    @Schema(description = "领取时间。")
    private LocalDateTime claimedAt;

    @Schema(description = "开始处理时间。")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间。")
    private LocalDateTime finishedAt;

    @Schema(description = "创建任务请求 ID。")
    private String requestId;

    @Schema(description = "幂等键。")
    private String idempotencyKey;

    @Schema(description = "请求摘要。")
    private String requestHash;

    @Schema(description = "创建成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "任务领取和状态流转乐观锁。")
    private Integer version;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
