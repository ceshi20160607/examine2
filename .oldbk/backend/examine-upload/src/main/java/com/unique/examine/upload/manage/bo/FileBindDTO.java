package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件绑定入参。
 */
@Data
@Schema(description = "文件绑定入参")
public class FileBindDTO {

    @Schema(description = "文件 ID")
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    @Schema(description = "引用业务类型，例如 MODULE_RECORD_FIELD、EXPORT_RESULT、FLOW_COMMENT")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "业务记录 ID")
    private Long recordId;

    @Schema(description = "附件字段编码")
    private String fieldCode;

    @Schema(description = "展示文件名")
    private String displayName;

    @Schema(description = "排序")
    private Integer sortOrder;
}
