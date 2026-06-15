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
 * 外部写接口幂等记录、请求摘要和结果快照。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_idempotency_record")
@Schema(name = "IdempotencyRecord", description = "外部写接口幂等记录、请求摘要和结果快照。")
public class IdempotencyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "OpenAPI 客户端 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "API ID，如 OPN-003。")
    private String apiId;

    @Schema(description = "业务动作。")
    private String bizAction;

    @Schema(description = "幂等键。")
    private String idempotencyKey;

    @Schema(description = "归一化幂等 scope。")
    private String scopeKey;

    @Schema(description = "请求摘要 SHA-256。")
    private String requestHash;

    @Schema(description = "PROCESSING、SUCCESS、FAILED、CONFLICT。")
    private String status;

    @Schema(description = "结果快照，含 code、success、data 标识、requestId。")
    private String resultSnapshotJson;

    @Schema(description = "签名结果。")
    private String signatureResult;

    @Schema(description = "scope 命中结果。")
    private String scopeResult;

    @Schema(description = "首次请求 requestId。")
    private String requestId;

    @Schema(description = "幂等记录过期时间，默认 72 小时且不小于 24 小时。")
    private LocalDateTime expiresAt;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
