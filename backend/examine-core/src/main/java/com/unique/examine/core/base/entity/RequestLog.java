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
 * 内部请求日志。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_sys_request_log")
@Schema(name = "RequestLog", description = "内部请求日志。")
public class RequestLog implements Serializable {

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

    @Schema(description = "系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "HTTP 方法。")
    private String method;

    @Schema(description = "请求路径。")
    private String path;

    @Schema(description = "模块命名空间。")
    private String module;

    @Schema(description = "客户端 IP。")
    private String clientIp;

    @Schema(description = "HTTP 状态码。")
    private Integer httpStatus;

    @Schema(description = "SUCCESS、FAILED。")
    private String result;

    @Schema(description = "耗时毫秒。")
    private Integer durationMs;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
