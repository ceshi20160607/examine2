package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色 OpenAPI scope 授权入参。
 */
@Data
@Schema(description = "角色 OpenAPI scope 授权入参")
public class OpenapiScopeBO {

    @Schema(description = "租户 ID，0 表示系统级")
    private String tenantId;

    @NotBlank(message = "scope 编码不能为空")
    @Schema(description = "OpenAPI scope 编码")
    private String scopeCode;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "字段编码范围")
    private List<String> fieldCodes;

    @Schema(description = "scope 动作：READ、WRITE、FLOW_ACTION、FILE_DOWNLOAD")
    private String scopeAction;
}
