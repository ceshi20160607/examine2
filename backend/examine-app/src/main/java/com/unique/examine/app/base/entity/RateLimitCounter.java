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
 * 限流窗口计数。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_rate_limit_counter")
@Schema(name = "RateLimitCounter", description = "限流窗口计数。")
public class RateLimitCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "限流策略 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long policyId;

    @Schema(description = "clientId + systemId + tenantId + apiId + scopeCode + sourceIp。")
    private String dimensionKey;

    @Schema(description = "窗口开始时间。")
    private LocalDateTime windowStartAt;

    @Schema(description = "窗口结束时间。")
    private LocalDateTime windowEndAt;

    @Schema(description = "当前窗口计数。")
    private Integer requestCount;

    @Schema(description = "最近一次 requestId。")
    private String lastRequestId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
