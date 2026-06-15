package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件查询入参。
 */
@Data
@Schema(description = "文件查询入参")
public class FileQueryBO {

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "关键字，匹配文件名或扩展名")
    private String keyword;

    @Schema(description = "文件状态：TEMP、REFERENCED、DELETED、EXPIRED")
    private String status;

    @Schema(description = "引用业务类型")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "业务记录 ID")
    private Long recordId;

    @Schema(description = "附件字段编码")
    private String fieldCode;
}
