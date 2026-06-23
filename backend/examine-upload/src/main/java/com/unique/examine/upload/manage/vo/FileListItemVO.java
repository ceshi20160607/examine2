package com.unique.examine.upload.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件列表项。
 */
@Data
@Builder
@Schema(description = "文件列表项")
public class FileListItemVO {

    @Schema(description = "文件 ID")
    private String fileId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "扩展名")
    private String extension;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "文件大小")
    private Long size;

    @Schema(description = "文件状态")
    private String status;

    @Schema(description = "是否可预览")
    private Boolean previewable;

    @Schema(description = "下载地址")
    private String downloadUrl;

    @Schema(description = "上传人成员 ID")
    private String ownerMemberId;

    @Schema(description = "引用数量")
    private Integer refCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
