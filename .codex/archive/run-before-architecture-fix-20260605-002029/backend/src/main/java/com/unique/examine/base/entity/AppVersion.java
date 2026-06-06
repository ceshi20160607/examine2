package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_version")
@Schema(description = "app_version 表实体")
public class AppVersion {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "app_id")
    @TableField("app_id")
    private Long appId;

    @Schema(description = "version_no")
    @TableField("version_no")
    private Integer versionNo;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "version_note")
    @TableField("version_note")
    private String versionNote;

    @Schema(description = "published_at")
    @TableField("published_at")
    private LocalDateTime publishedAt;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
