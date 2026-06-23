package com.unique.examine.plat.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录主体回显。
 */
@Data
@Builder
@Schema(description = "登录主体回显")
public class AuthAccountVO {

    @Schema(description = "平台账号 ID")
    private String accountId;

    @Schema(description = "登录名")
    private String loginName;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "账号状态")
    private String status;
}
