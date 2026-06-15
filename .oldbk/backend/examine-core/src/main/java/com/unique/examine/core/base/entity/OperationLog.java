package com.unique.examine.core.base.entity;

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
 * 平台、系统、运行、流程、文件和 OpenAPI 操作审计。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_audit_operation_log")
@Schema(name = "OperationLog", description = "平台、系统、运行、流程、文件和 OpenAPI 操作审计。")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "链路追踪 ID。")
    private String traceId;

    @Schema(description = "ACCOUNT、MEMBER、OPENAPI_CLIENT、SYSTEM。")
    private String operatorType;

    @Schema(description = "操作主体 ID。")
    private String operatorId;

    @Schema(description = "操作主体名称快照。")
    private String operatorName;

    @Schema(description = "系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "模块，如 FLOW、UPLOAD、OPENAPI。")
    private String module;

    @Schema(description = "业务对象类型。")
    private String bizType;

    @Schema(description = "业务对象 ID。")
    private String bizId;

    @Schema(description = "操作动作。")
    private String action;

    @Schema(description = "SUCCESS、FAILED。")
    private String result;

    @Schema(description = "错误码。")
    private String errorCode;

    @Schema(description = "审计摘要。")
    private String summary;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
