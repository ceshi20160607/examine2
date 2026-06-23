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
 * 导出任务状态流转、领取、失败和重试日志。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_job_log")
@Schema(name = "ExportJobLog", description = "导出任务状态流转、领取、失败和重试日志。")
public class ExportJobLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志 ID。")
    @TableId(value = "log_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long logId;

    @Schema(description = "导出任务 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "日志类型。")
    private String logType;

    @Schema(description = "变更前状态。")
    private String fromStatus;

    @Schema(description = "变更后状态。")
    private String toStatus;

    @Schema(description = "日志说明。")
    private String message;

    @Schema(description = "领取、失败、重试等上下文快照。")
    private String snapshotJson;

    @Schema(description = "请求或后台任务追踪 ID。")
    private String requestId;

    @Schema(description = "操作成员 ID或 runner 标识。")
    private String operatorId;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;

    @Schema(description = "主键，雪花 ID 或等价全局唯一 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "逻辑删除标记，0-正常，1-删除。")
    private Byte deleted;

    @Schema(description = "创建人平台账号或系统成员 ID，按业务上下文解释。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "更新人平台账号或系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;
}
