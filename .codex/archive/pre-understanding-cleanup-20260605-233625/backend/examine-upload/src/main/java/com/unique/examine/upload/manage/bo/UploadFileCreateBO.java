package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件元数据登记入参。
 */
@Data
@Schema(description = "文件元数据登记入参")
public class UploadFileCreateBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "存储配置 ID")
    private Long storageConfigId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "文件扩展名")
    private String fileExt;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "文件大小，字节")
    private Long fileSize;

    @Schema(description = "存储路径")
    private String storagePath;

    @Schema(description = "文件 sha256")
    private String sha256;
}
