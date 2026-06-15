package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 切换租户上下文入参。
 */
@Data
@Schema(description = "切换租户上下文入参")
public class TenantSwitchBO {

    @NotBlank(message = "租户 ID 不能为空")
    @Schema(description = "目标租户 ID")
    private String tenantId;
}
