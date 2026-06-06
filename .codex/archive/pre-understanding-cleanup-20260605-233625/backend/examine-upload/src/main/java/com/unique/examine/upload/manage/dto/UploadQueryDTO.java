package com.unique.examine.upload.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件查询 DTO。
 */
@Data
@Schema(description = "文件查询 DTO")
public class UploadQueryDTO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "文件状态")
    private String status;
}
