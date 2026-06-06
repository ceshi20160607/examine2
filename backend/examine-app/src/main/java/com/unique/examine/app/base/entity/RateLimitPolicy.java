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
 * 客户端限流策略。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_rate_limit_policy")
@Schema(name = "RateLimitPolicy", description = "客户端限流策略。")
public class RateLimitPolicy implements Serializable {

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

    @Schema(description = "API ID；为空表示客户端默认策略。")
    private String apiId;

    @Schema(description = "scope；为空表示通用策略。")
    private String scopeCode;

    @Schema(description = "来源 IP 限定；为空表示不限定。")
    private String sourceIp;

    @Schema(description = "限流窗口秒数。")
    private Integer windowSeconds;

    @Schema(description = "窗口最大请求数。")
    private Integer maxRequests;

    @Schema(description = "突发额度。")
    private Integer burst;

    @Schema(description = "生效时间。")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间。")
    private LocalDateTime effectiveTo;

    @Schema(description = "ENABLED、DISABLED。")
    private String status;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
