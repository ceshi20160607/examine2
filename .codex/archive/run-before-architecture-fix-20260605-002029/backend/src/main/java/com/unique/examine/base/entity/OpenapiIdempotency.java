package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("openapi_idempotency")
@Schema(description = "openapi_idempotency 表实体")
public class OpenapiIdempotency {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "client_pk")
    @TableField("client_pk")
    private Long clientPk;

    @Schema(description = "idempotency_key")
    @TableField("idempotency_key")
    private String idempotencyKey;

    @Schema(description = "request_hash")
    @TableField("request_hash")
    private String requestHash;

    @Schema(description = "response_snapshot")
    @TableField("response_snapshot")
    private String responseSnapshot;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
