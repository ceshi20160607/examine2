package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("tenant")
@Schema(description = "tenant 表实体")
public class Tenant {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "tenant_name")
    @TableField("tenant_name")
    private String tenantName;

    @Schema(description = "tenant_code")
    @TableField("tenant_code")
    private String tenantCode;

    @Schema(description = "owner_account_id")
    @TableField("owner_account_id")
    private Long ownerAccountId;

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
