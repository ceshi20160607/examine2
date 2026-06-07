package com.unique.examine.web.audit.bo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 审计日志查询入参。
 */
@Data
@Schema(description = "审计日志查询入参")
public class AuditLogQueryBO {

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务 ID")
    private String bizId;

    @Schema(description = "操作动作")
    private String action;

    @Schema(description = "结果")
    private String result;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "开始时间")
    private LocalDateTime startAt;

    @Schema(description = "结束时间")
    private LocalDateTime endAt;
}
