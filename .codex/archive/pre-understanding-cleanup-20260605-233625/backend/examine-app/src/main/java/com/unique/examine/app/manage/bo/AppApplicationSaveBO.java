package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用保存入参。
 */
@Data
@Schema(description = "应用保存入参")
public class AppApplicationSaveBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用编码")
    private String appCode;

    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "可见范围：TENANT、ROLE、CUSTOM")
    private String visibleScope;

    @Schema(description = "应用说明")
    private String description;
}
