package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("file_object")
@Schema(description = "file_object 表实体")
public class FileObject {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "storage_path")
    @TableField("storage_path")
    private String storagePath;

    @Schema(description = "file_name")
    @TableField("file_name")
    private String fileName;

    @Schema(description = "file_size")
    @TableField("file_size")
    private Long fileSize;

    @Schema(description = "content_type")
    @TableField("content_type")
    private String contentType;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "created_by")
    @TableField("created_by")
    private Long createdBy;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
