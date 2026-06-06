package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("openapi_credential")
@Schema(description = "openapi_credential 表实体")
public class OpenapiCredential {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "client_pk")
    @TableField("client_pk")
    private Long clientPk;

    @Schema(description = "key_version")
    @TableField("key_version")
    private Integer keyVersion;

    @Schema(description = "secret_digest")
    @TableField("secret_digest")
    private String secretDigest;

    @Schema(description = "expires_at")
    @TableField("expires_at")
    private LocalDateTime expiresAt;

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
