package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("global_config")
@Schema(description = "global_config 表实体")
public class GlobalConfig {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "config_key")
    @TableField("config_key")
    private String configKey;

    @Schema(description = "config_value")
    @TableField("config_value")
    private String configValue;

    @Schema(description = "secret_placeholder_flag")
    @TableField("secret_placeholder_flag")
    private Integer secretPlaceholderFlag;

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
