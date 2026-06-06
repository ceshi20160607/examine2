package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("page_config")
@Schema(description = "page_config 表实体")
public class PageConfig {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "page_type")
    @TableField("page_type")
    private String pageType;

    @Schema(description = "app_version_id")
    @TableField("app_version_id")
    private Long appVersionId;

    @Schema(description = "layout_json")
    @TableField("layout_json")
    private String layoutJson;

    @Schema(description = "block_json")
    @TableField("block_json")
    private String blockJson;

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
