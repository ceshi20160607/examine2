package com.unique.examine.upload.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件引用展示对象。
 */
@Data
@Builder
@Schema(description = "文件引用展示对象")
public class FileReferenceVO {

    @Schema(description = "引用 ID")
    private String referenceId;

    @Schema(description = "文件 ID")
    private String fileId;

    @Schema(description = "引用业务类型")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private String bizId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "业务记录 ID")
    private String recordId;

    @Schema(description = "附件字段编码")
    private String fieldCode;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "引用状态")
    private String status;

    @Schema(description = "绑定时间")
    private LocalDateTime boundAt;
}
