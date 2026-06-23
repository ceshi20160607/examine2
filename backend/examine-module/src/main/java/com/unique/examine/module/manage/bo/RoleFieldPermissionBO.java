package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色字段权限保存入参。
 */
@Data
@Schema(description = "角色字段权限保存入参")
public class RoleFieldPermissionBO {

    @Schema(description = "租户 ID，0 表示系统级")
    private String tenantId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "字段 ID")
    private String fieldId;

    @NotBlank(message = "字段编码不能为空")
    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段是否可见")
    private Boolean visible;

    @Schema(description = "字段是否可写")
    private Boolean writable;

    @Schema(description = "导出时是否允许明文")
    private Boolean exportPlain;

    @Schema(description = "OpenAPI 是否可读")
    private Boolean openapiReadable;

    @Schema(description = "OpenAPI 是否可写")
    private Boolean openapiWritable;
}
