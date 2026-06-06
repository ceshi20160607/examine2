package com.unique.examine.upload.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传中心统一出参。
 */
@Data
@Schema(description = "上传中心统一出参")
public class UploadManageVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "大小")
    private Long size;

    @Schema(description = "存储路径")
    private String storagePath;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "失败原因")
    private String failureReason;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
