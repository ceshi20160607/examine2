package com.unique.examine.upload.manage.vo;

import org.springframework.core.io.Resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件访问结果。
 */
@Data
@Builder
@Schema(description = "文件访问结果")
public class FileAccessVO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "文件大小")
    private Long size;

    @Schema(description = "是否内联预览")
    private Boolean inline;

    @Schema(description = "文件资源")
    private Resource resource;
}
