package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("openapi_scope")
@Schema(description = "openapi_scope 表实体")
public class OpenapiScope {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "client_pk")
    @TableField("client_pk")
    private Long clientPk;

    @Schema(description = "scope_type")
    @TableField("scope_type")
    private String scopeType;

    @Schema(description = "scope_value")
    @TableField("scope_value")
    private String scopeValue;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
