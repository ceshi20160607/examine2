package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统租户信息。
 */
@Data
@Builder
@Schema(description = "系统租户信息")
public class TenantVO {

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "所属系统 ID")
    private String systemId;

    @Schema(description = "租户编码")
    private String code;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户状态：ENABLED、DISABLED")
    private String status;

    @Schema(description = "租户描述")
    private String description;
}
