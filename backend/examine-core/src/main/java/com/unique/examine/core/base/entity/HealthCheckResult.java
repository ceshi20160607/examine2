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
 * 健康检查结果。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_sys_health_check_result")
@Schema(name = "HealthCheckResult", description = "健康检查结果。")
public class HealthCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "组件，如 DB、REDIS、UPLOAD_STORAGE、OPENAPI_KEY。")
    private String component;

    @Schema(description = "UP、WARN、DOWN。")
    private String status;

    @Schema(description = "SUCCESS、FAILED。")
    private String result;

    @Schema(description = "检查消息。")
    private String message;

    @Schema(description = "修复建议。")
    private String suggestion;

    @Schema(description = "检查时间。")
    private LocalDateTime checkedAt;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
