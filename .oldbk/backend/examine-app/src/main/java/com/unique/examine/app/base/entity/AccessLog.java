package com.unique.examine.app.base.entity;

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
 * 外部调用日志。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_access_log")
@Schema(name = "AccessLog", description = "外部调用日志。")
public class AccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "链路追踪 ID。")
    private String traceId;

    @Schema(description = "客户端 ID；AK 无效时可为空。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "AK，必要时脱敏。")
    private String accessKey;

    @Schema(description = "系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "API ID。")
    private String apiId;

    @Schema(description = "HTTP 方法。")
    private String method;

    @Schema(description = "请求路径。")
    private String path;

    @Schema(description = "来源 IP。")
    private String sourceIp;

    @Schema(description = "请求 body SHA-256。")
    private String bodyHash;

    @Schema(description = "PASS、FAIL、NOT_CHECKED。")
    private String signatureResult;

    @Schema(description = "PASS、REPLAY、NOT_CHECKED。")
    private String nonceResult;

    @Schema(description = "PASS、DENIED、NOT_CHECKED。")
    private String scopeResult;

    @Schema(description = "PASS、LIMITED、NOT_CHECKED。")
    private String rateLimitResult;

    @Schema(description = "NEW、REPLAY、CONFLICT、PROCESSING、NOT_CHECKED。")
    private String idempotencyResult;

    @Schema(description = "SUCCESS、FAILED。")
    private String result;

    @Schema(description = "HTTP 状态码。")
    private Integer httpStatus;

    @Schema(description = "错误码。")
    private String errorCode;

    @Schema(description = "耗时毫秒。")
    private Integer durationMs;

    @Schema(description = "业务对象类型。")
    private String bizType;

    @Schema(description = "业务对象 ID。")
    private String bizId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
