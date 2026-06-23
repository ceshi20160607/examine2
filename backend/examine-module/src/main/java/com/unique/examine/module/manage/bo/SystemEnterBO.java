package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 进入系统入参。
 */
@Data
@Schema(description = "进入系统入参")
public class SystemEnterBO {

    @Schema(description = "进入系统时选择的租户 ID，不传则使用成员默认租户")
    private String tenantId;
}
