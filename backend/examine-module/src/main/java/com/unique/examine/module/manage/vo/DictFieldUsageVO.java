package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字典字段引用摘要。
 */
@Data
@Builder
@Schema(description = "字典字段引用摘要")
public class DictFieldUsageVO {

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "字段 ID")
    private String fieldId;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "发布版本 ID")
    private String publishedVersionId;

    @Schema(description = "引用状态")
    private String status;
}
