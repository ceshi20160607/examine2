package com.unique.examine.upload.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件详情展示对象。
 */
@Data
@Builder
@Schema(description = "文件详情展示对象")
public class FileInfoVO {

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

    @Schema(description = "预览不可用原因")
    private String previewableReason;

    @Schema(description = "是否可下载")
    private Boolean downloadable;

    @Schema(description = "下载不可用原因")
    private String downloadableReason;

    @Schema(description = "下载地址")
    private String downloadUrl;

    @Schema(description = "上传人成员 ID")
    private String ownerMemberId;

    @Schema(description = "引用数量")
    private Integer refCount;

    @Schema(description = "临时文件过期时间")
    private LocalDateTime tempExpiresAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "文件引用列表")
    private List<FileReferenceVO> references;
}
