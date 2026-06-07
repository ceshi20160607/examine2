package com.unique.examine.web.audit.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 审计日志详情。
 */
@Data
@Builder
@Schema(description = "审计日志详情")
public class AuditLogDetailVO {

    @Schema(description = "日志 ID")
    private Long logId;

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "traceId")
    private String traceId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "操作主体类型")
    private String operatorType;

    @Schema(description = "操作主体 ID")
    private String operatorId;

    @Schema(description = "操作主体名称")
    private String operatorName;

    @Schema(description = "模块")
    private String module;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务 ID")
    private String bizId;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "结果")
    private String result;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "前后快照")
    private List<BeforeAfterSnapshotVO> snapshots;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
