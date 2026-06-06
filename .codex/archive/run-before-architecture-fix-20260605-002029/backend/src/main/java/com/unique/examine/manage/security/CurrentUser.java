package com.unique.examine.manage.security;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前登录用户")
public class CurrentUser {
    @Schema(description = "账号ID")
    private Long accountId;
    @Schema(description = "登录账号")
    private String account;
    @Schema(description = "姓名")
    private String realName;
    @Schema(description = "当前系统ID")
    private Long systemId;
    @Schema(description = "当前租户ID")
    private Long tenantId;
}
