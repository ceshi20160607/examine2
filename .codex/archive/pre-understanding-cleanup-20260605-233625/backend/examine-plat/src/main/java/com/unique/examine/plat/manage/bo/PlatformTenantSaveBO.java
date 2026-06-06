package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户保存入参。
 */
@Data
@Schema(description = "租户保存入参")
public class PlatformTenantSaveBO {

    @Schema(description = "租户编码，全局唯一")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "默认管理员账号 ID")
    private Long adminAccountId;

    @Schema(description = "租户有效期截止时间")
    private LocalDateTime expireAt;

    @Schema(description = "租户扩展配置 JSON")
    private String configJson;
}
