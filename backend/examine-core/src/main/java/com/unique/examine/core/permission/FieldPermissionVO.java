package com.unique.examine.core.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字段级权限。
 */
@Data
@Builder
@Schema(description = "字段级权限")
public class FieldPermissionVO {

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "是否可见")
    private boolean visible;

    @Schema(description = "是否可写")
    private boolean writable;

    @Schema(description = "导出时是否允许明文")
    private boolean exportPlain;

    @Schema(description = "OpenAPI 是否可读")
    private boolean openapiReadable;

    @Schema(description = "OpenAPI 是否可写")
    private boolean openapiWritable;
}
