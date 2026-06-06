package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("business_record")
@Schema(description = "business_record 表实体")
public class BusinessRecord {

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

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "record_no")
    @TableField("record_no")
    private String recordNo;

    @Schema(description = "record_status")
    @TableField("record_status")
    private String recordStatus;

    @Schema(description = "process_status")
    @TableField("process_status")
    private String processStatus;

    @Schema(description = "app_version_id")
    @TableField("app_version_id")
    private Long appVersionId;

    @Schema(description = "config_snapshot")
    @TableField("config_snapshot")
    private String configSnapshot;

    @Schema(description = "is_deleted")
    @TableField("is_deleted")
    private Integer isDeleted;

    @Schema(description = "created_by")
    @TableField("created_by")
    private Long createdBy;

    @Schema(description = "updated_by")
    @TableField("updated_by")
    private Long updatedBy;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
